package com.example.promac;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // UI Wi-Fi
    private TextView tvWifiResult;
    private EditText etWifiMac;
    private Button btnWriteWifi, btnCopyWifi;
    private CheckBox cbGenOnReset;
    private String currentWifiMac = "";

    // UI Bluetooth
    private TextView tvBtResult;
    private EditText etBtMac;
    private Button btnWriteBt, btnCopyBt;
    private String currentBtMac = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Inicijalizacija Wi-Fi elemenata
        tvWifiResult = findViewById(R.id.tvWifiResult);
        etWifiMac = findViewById(R.id.etWifiMac);
        cbGenOnReset = findViewById(R.id.cbGenOnReset);
        Button btnReadWifi = findViewById(R.id.btnReadWifi);
        btnWriteWifi = findViewById(R.id.btnWriteWifi);
        Button btnGenerateWifi = findViewById(R.id.btnGenerateWifi);
        btnCopyWifi = findViewById(R.id.btnCopyWifi);

        // 2. Inicijalizacija Bluetooth elemenata
        tvBtResult = findViewById(R.id.tvBtResult);
        etBtMac = findViewById(R.id.etBtMac);
        Button btnReadBt = findViewById(R.id.btnReadBt);
        btnWriteBt = findViewById(R.id.btnWriteBt);
        Button btnGenerateBt = findViewById(R.id.btnGenerateBt);
        btnCopyBt = findViewById(R.id.btnCopyBt);

        // Auto-format unosa
        setupMacFormatting(etWifiMac);
        setupMacFormatting(etBtMac);

        // Wi-Fi Akcije
        btnReadWifi.setOnClickListener(v -> readMacAddress());
        btnWriteWifi.setOnClickListener(v -> confirmWriteMacAddress());
        btnGenerateWifi.setOnClickListener(v -> etWifiMac.setText(generateRandomMac()));
        btnCopyWifi.setOnClickListener(v -> copyToClipboard("Wi-Fi MAC", currentWifiMac));

        // Bluetooth Akcije
        btnReadBt.setOnClickListener(v -> readBtMacAddress());
        btnWriteBt.setOnClickListener(v -> confirmWriteBtMacAddress());
        btnGenerateBt.setOnClickListener(v -> etBtMac.setText(generateRandomMac()));
        btnCopyBt.setOnClickListener(v -> copyToClipboard("Bluetooth MAC", currentBtMac));
    }

    private void setupMacFormatting(EditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;

                String clean = s.toString().replaceAll("[^A-Fa-f0-9]", "");
                if (clean.length() > 12) {
                    clean = clean.substring(0, 12);
                }

                StringBuilder formatted = new StringBuilder();
                for (int i = 0; i < clean.length(); i++) {
                    if (i > 0 && i % 2 == 0) {
                        formatted.append(":");
                    }
                    formatted.append(clean.charAt(i));
                }

                s.replace(0, s.length(), formatted.toString().toUpperCase());
                isFormatting = false;
            }
        });
    }

    private String generateRandomMac() {
        SecureRandom random = new SecureRandom();
        byte[] macBytes = new byte[6];
        random.nextBytes(macBytes);

        // Unicast + Locally Administered MAC adresa
        macBytes[0] = (byte) ((macBytes[0] & 0xFE) | 0x02);

        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                macBytes[0], macBytes[1], macBytes[2],
                macBytes[3], macBytes[4], macBytes[5]);
    }

    // ==================== WI-FI LOGIKA ====================

    private void readMacAddress() {
        tvWifiResult.setText("Reading...");

        executor.execute(() -> {
            String destPath = getFilesDir().getAbsolutePath() + "/WIFI";

            ArrayList<String> cmds = new ArrayList<>();
            cmds.add("cp -rp /data/nvram/APCFG/APRDEB/WIFI " + destPath);
            cmds.add("chmod 0777 " + destPath);
            executeRootCmds(cmds);

            File localFile = new File(destPath);
            byte[] fileContent = new byte[512];
            boolean readSuccess = false;

            if (localFile.exists()) {
                try (FileInputStream fin = new FileInputStream(localFile)) {
                    int read = fin.read(fileContent);
                    if (read >= 10) {
                        readSuccess = true;
                    }
                } catch (IOException ignored) {}
            }

            final boolean success = readSuccess;
            final byte[] data = fileContent;

            mainHandler.post(() -> {
                if (!success) {
                    tvWifiResult.setText("WiFi Mac:\nError reading file");
                    btnWriteWifi.setEnabled(false);
                    btnCopyWifi.setEnabled(false);
                    return;
                }

                if (data[4] == -95 && cbGenOnReset != null) {
                    cbGenOnReset.setChecked(true);
                }

                String mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                        data[4], data[5], data[6], data[7], data[8], data[9]);

                currentWifiMac = mac;
                tvWifiResult.setText("Read WiFi Mac:\n" + mac);
                btnWriteWifi.setEnabled(true);
                btnCopyWifi.setEnabled(true);
            });
        });
    }

    private void confirmWriteMacAddress() {
        boolean autoGen = (cbGenOnReset != null && cbGenOnReset.isChecked());
        String rawMac = etWifiMac.getText().toString().trim();

        if (!autoGen && !isValidMac(rawMac)) {
            Toast.makeText(this, "Your MAC is invalid.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you confirm changing the Wi-Fi MAC?")
            .setPositiveButton("Change", (dialog, which) -> startWriteProcess(rawMac, autoGen))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startWriteProcess(String rawMac, boolean autoGen) {
        Toast.makeText(this, "Changing Wi-Fi MAC...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            String[] b;
            if (autoGen) {
                b = new String[]{"A1", "02", "02", "02", "02", "03"};
            } else {
                b = rawMac.split(":");
            }

            String destPath = getFilesDir().getAbsolutePath() + "/WIFI";
            File localFile = new File(destPath);
            byte[] fileContent = new byte[512];

            if (localFile.exists()) {
                try (FileInputStream fin = new FileInputStream(localFile)) {
                    fin.read(fileContent);
                } catch (IOException ignored) {}
            }

            fileContent[4] = hexToByte(b[0]);
            fileContent[5] = hexToByte(b[1]);
            fileContent[6] = hexToByte(b[2]);
            fileContent[7] = hexToByte(b[3]);
            fileContent[8] = hexToByte(b[4]);
            fileContent[9] = hexToByte(b[5]);

            try (FileOutputStream file = new FileOutputStream(destPath)) {
                file.write(fileContent);
            } catch (IOException e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error in MAC changing.", Toast.LENGTH_SHORT).show());
                return;
            }

            boolean wifiEnabled = false;
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            try {
                if (wifi != null && wifi.isWifiEnabled()) {
                    wifiEnabled = true;
                    wifi.setWifiEnabled(false);
                }
            } catch (Exception ignored) {}

            ArrayList<String> cmds = new ArrayList<>();
            cmds.add("cp -rp " + destPath + " /data/nvram/APCFG/APRDEB/WIFI");
            cmds.add("chmod 660 /data/nvram/APCFG/APRDEB/WIFI");
            cmds.add("chown root.nvram /data/nvram/APCFG/APRDEB/WIFI");

            cmds.add("cp -rp " + destPath + " /data/nvdata/APCFG/APRDEB/WIFI 2>/dev/null || true");
            cmds.add("chmod 660 /data/nvdata/APCFG/APRDEB/WIFI 2>/dev/null || true");
            cmds.add("chown root.nvram /data/nvdata/APCFG/APRDEB/WIFI 2>/dev/null || true");

            executeRootCmds(cmds);

            if (wifiEnabled && wifi != null) {
                try {
                    wifi.setWifiEnabled(true);
                } catch (Exception ignored) {}
            }

            mainHandler.post(() -> Toast.makeText(MainActivity.this, "Wi-Fi MAC address changed successfully.", Toast.LENGTH_LONG).show());
        });
    }

    // ==================== BLUETOOTH LOGIKA ====================

    private void readBtMacAddress() {
        tvBtResult.setText("Reading Bluetooth MAC...");

        executor.execute(() -> {
            String destPath = getFilesDir().getAbsolutePath() + "/BT_ADDR";
            byte[] fileContent = new byte[512];
            boolean readSuccess = false;
            String errorMsg = "";

            try {
                // Prvo pokušaj da kopiraš fajl (isto kao za Wi-Fi)
                ArrayList<String> cmds = new ArrayList<>();
                cmds.add("cp -rp /data/nvram/APCFG/APRDEB/BT_ADDR " + destPath + " 2>/dev/null");
                cmds.add("chmod 0777 " + destPath);
                executeRootCmds(cmds);

                File localFile1 = new File(destPath);
                
                if (localFile1.exists()) {
                    try (FileInputStream fin = new FileInputStream(localFile1)) {
                        int bytesRead = fin.read(fileContent);
                        if (bytesRead >= 6) {
                            readSuccess = true;
                        } else {
                            errorMsg = "Read only " + bytesRead + " bytes from copied file";
                        }
                    } catch (IOException e) {
                        errorMsg = "Error reading copied file: " + e.getMessage();
                    }
                } else {
                    errorMsg = "File not found after copy attempt";
                }

                // Ako prva metoda ne uspe, pokušaj sa drugom putanjom
                if (!readSuccess) {
                    String destPath2 = getFilesDir().getAbsolutePath() + "/BT_ADDR2";
                    ArrayList<String> cmds2 = new ArrayList<>();
                    cmds2.add("cp -rp /data/nvdata/APCFG/APRDEB/BT_ADDR " + destPath2 + " 2>/dev/null");
                    cmds2.add("chmod 0777 " + destPath2);
                    executeRootCmds(cmds2);

                    File localFile2 = new File(destPath2);
                    
                    if (localFile2.exists()) {
                        try (FileInputStream fin = new FileInputStream(localFile2)) {
                            int bytesRead = fin.read(fileContent);
                            if (bytesRead >= 6) {
                                readSuccess = true;
                            } else {
                                errorMsg = "Read only " + bytesRead + " bytes from second copied file";
                            }
                        } catch (IOException e) {
                            errorMsg = "Error reading second copied file: " + e.getMessage();
                        }
                    } else {
                        errorMsg = "Second file not found after copy attempt";
                    }
                }

                // Ako ništa ne radi, probaj da kreiraš default fajl
                if (!readSuccess) {
                    errorMsg = "Failed to read from both paths. Creating default BT_ADDR file...";
                    
                    // Kreiraj default fajl sa MAC adresom 00:00:00:00:00:00
                    byte[] defaultData = new byte[6];
                    for (int i = 0; i < 6; i++) {
                        defaultData[i] = 0x00;
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(destPath)) {
                        fos.write(defaultData);
                    } catch (IOException e) {
                        errorMsg = "Failed to create default file: " + e.getMessage();
                    }
                    
                    // Pokušaj ponovo da pročitaš
                    File localFile3 = new File(destPath);
                    if (localFile3.exists()) {
                        try (FileInputStream fin = new FileInputStream(localFile3)) {
                            int bytesRead = fin.read(fileContent);
                            if (bytesRead >= 6) {
                                readSuccess = true;
                                errorMsg = "Created default file with MAC 00:00:00:00:00:00";
                            }
                        } catch (IOException e) {
                            errorMsg = "Error reading created file: " + e.getMessage();
                        }
                    }
                }

            } catch (Exception e) {
                errorMsg = "Exception: " + e.getMessage();
                e.printStackTrace();
            }

            final boolean success = readSuccess;
            final byte[] data = fileContent;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                if (!success) {
                    tvBtResult.setText("Bluetooth Mac:\nREAD FAILED!\n\n" +
                        "Error: " + finalError + "\n\n" +
                        "Tried copying from:\n" +
                        "/data/nvram/APCFG/APRDEB/BT_ADDR\n" +
                        "/data/nvdata/APCFG/APRDEB/BT_ADDR\n\n" +
                        "Please check:\n" +
                        "1. File exists\n" +
                        "2. Root permissions granted\n" +
                        "3. File permissions are correct");
                    btnWriteBt.setEnabled(false);
                    btnCopyBt.setEnabled(false);
                    return;
                }

                // MAC adresa je na pozicijama 0-5
                String mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                        data[0], data[1], data[2], data[3], data[4], data[5]);

                currentBtMac = mac;
                tvBtResult.setText("Read Bluetooth Mac:\n" + mac + 
                    "\n\nInfo: " + finalError);
                btnWriteBt.setEnabled(true);
                btnCopyBt.setEnabled(true);
                Toast.makeText(MainActivity.this, "Bluetooth MAC read: " + mac, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void confirmWriteBtMacAddress() {
        String rawMac = etBtMac.getText().toString().trim();

        if (!isValidMac(rawMac)) {
            Toast.makeText(this, "Your BT MAC is invalid.", Toast.LENGTH_LONG).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Confirmation")
            .setMessage("Do you confirm changing the Bluetooth MAC?")
            .setPositiveButton("Change", (dialog, which) -> startWriteBtProcess(rawMac))
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void startWriteBtProcess(String rawMac) {
        Toast.makeText(this, "Changing BT MAC...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            String[] b = rawMac.split(":");
            String destPath = getFilesDir().getAbsolutePath() + "/BT_ADDR";
            boolean success = false;
            String errorMsg = "";

            try {
                // Prvo pročitaj postojeći fajl
                File localFile = new File(destPath);
                byte[] fileContent = new byte[512];
                int totalBytes = 0;

                if (localFile.exists()) {
                    try (FileInputStream fin = new FileInputStream(localFile)) {
                        totalBytes = fin.read(fileContent);
                    } catch (IOException e) {
                        errorMsg = "Error reading file: " + e.getMessage();
                    }
                }

                if (totalBytes < 6) {
                    // Ako fajl ne postoji ili je mali, kreiraj novi
                    errorMsg = "Creating new file with default size";
                    fileContent = new byte[512];
                    totalBytes = 512;
                }

                // Zameni prvih 6 bajtova sa novom MAC adresom
                fileContent[0] = hexToByte(b[0]);
                fileContent[1] = hexToByte(b[1]);
                fileContent[2] = hexToByte(b[2]);
                fileContent[3] = hexToByte(b[3]);
                fileContent[4] = hexToByte(b[4]);
                fileContent[5] = hexToByte(b[5]);

                // Sačuvaj lokalno
                try (FileOutputStream file = new FileOutputStream(destPath)) {
                    file.write(fileContent, 0, totalBytes);
                } catch (IOException e) {
                    errorMsg = "Error saving file: " + e.getMessage();
                    final String finalErrorMsg = errorMsg;
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error: " + finalErrorMsg, Toast.LENGTH_LONG).show());
                    return;
                }

                // Root komande za upis na obe lokacije
                ArrayList<String> cmds = new ArrayList<>();
                
                // Prva lokacija
                cmds.add("cp -f " + destPath + " /data/nvram/APCFG/APRDEB/BT_ADDR");
                cmds.add("chmod 660 /data/nvram/APCFG/APRDEB/BT_ADDR");
                cmds.add("chown root.nvram /data/nvram/APCFG/APRDEB/BT_ADDR");
                
                // Druga lokacija
                cmds.add("cp -f " + destPath + " /data/nvdata/APCFG/APRDEB/BT_ADDR");
                cmds.add("chmod 660 /data/nvdata/APCFG/APRDEB/BT_ADDR");
                cmds.add("chown root.nvram /data/nvdata/APCFG/APRDEB/BT_ADDR");
                
                // Sinhronizuj
                cmds.add("sync");

                success = executeRootCmds(cmds);
                if (success) {
                    errorMsg = "Write successful";
                } else {
                    errorMsg = "Failed to execute root commands";
                }

            } catch (Exception e) {
                errorMsg = "Exception: " + e.getMessage();
                e.printStackTrace();
            }

            final boolean finalSuccess = success;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                if (finalSuccess) {
                    Toast.makeText(MainActivity.this, "Bluetooth MAC changed to: " + rawMac + 
                        "\nReboot device for changes to take effect.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Error writing BT MAC!\n" + finalError, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // ==================== POMOĆNE METODE ====================

    private byte hexToByte(String s) {
        return (byte) ((Character.digit(s.charAt(0), 16) << 4) + Character.digit(s.charAt(1), 16));
    }

    private boolean executeRootCmds(ArrayList<String> cmds) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            for (String tmp : cmds) {
                os.writeBytes(tmp + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    private boolean isValidMac(String mac) {
        return mac != null && mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " copied to clipboard!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
