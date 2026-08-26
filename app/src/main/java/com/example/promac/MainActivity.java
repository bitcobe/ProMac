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

import java.io.DataInputStream;
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
            String mac = null;
            boolean readSuccess = false;
            String errorMsg = "";

            try {
                // DIREKTNO ČITANJE PREKO ROOT-A - bez kopiranja fajla
                Process process = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(process.getOutputStream());
                
                // Koristimo dd da pročitamo prvih 6 bajtova
                os.writeBytes("dd if=/data/nvram/APCFG/APRDEB/BT_ADDR bs=1 count=6 2>/dev/null\n");
                os.writeBytes("exit\n");
                os.flush();

                // Čitamo binarni izlaz
                DataInputStream dis = new DataInputStream(process.getInputStream());
                byte[] buffer = new byte[6];
                int bytesRead = 0;
                
                // Čitamo dok ne dobijemo 6 bajtova
                while (bytesRead < 6) {
                    int read = dis.read(buffer, bytesRead, 6 - bytesRead);
                    if (read == -1) break;
                    bytesRead += read;
                }
                
                process.waitFor();
                
                if (bytesRead == 6) {
                    mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                            buffer[0], buffer[1], buffer[2], buffer[3], buffer[4], buffer[5]);
                    readSuccess = true;
                } else {
                    errorMsg = "Read only " + bytesRead + " bytes";
                }

                // Ako prva metoda ne radi, probaj sa cat
                if (!readSuccess) {
                    Process process2 = Runtime.getRuntime().exec("su");
                    DataOutputStream os2 = new DataOutputStream(process2.getOutputStream());
                    
                    os2.writeBytes("cat /data/nvram/APCFG/APRDEB/BT_ADDR 2>/dev/null\n");
                    os2.writeBytes("exit\n");
                    os2.flush();

                    DataInputStream dis2 = new DataInputStream(process2.getInputStream());
                    byte[] buffer2 = new byte[6];
                    int bytesRead2 = 0;
                    
                    while (bytesRead2 < 6) {
                        int read = dis2.read(buffer2, bytesRead2, 6 - bytesRead2);
                        if (read == -1) break;
                        bytesRead2 += read;
                    }
                    
                    process2.waitFor();
                    
                    if (bytesRead2 == 6) {
                        mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                                buffer2[0], buffer2[1], buffer2[2], buffer2[3], buffer2[4], buffer2[5]);
                        readSuccess = true;
                    } else {
                        errorMsg = "Cat read only " + bytesRead2 + " bytes";
                    }
                }

                // Ako ništa ne radi, probaj sa drugom putanjom
                if (!readSuccess) {
                    Process process3 = Runtime.getRuntime().exec("su");
                    DataOutputStream os3 = new DataOutputStream(process3.getOutputStream());
                    
                    os3.writeBytes("dd if=/data/nvdata/APCFG/APRDEB/BT_ADDR bs=1 count=6 2>/dev/null\n");
                    os3.writeBytes("exit\n");
                    os3.flush();

                    DataInputStream dis3 = new DataInputStream(process3.getInputStream());
                    byte[] buffer3 = new byte[6];
                    int bytesRead3 = 0;
                    
                    while (bytesRead3 < 6) {
                        int read = dis3.read(buffer3, bytesRead3, 6 - bytesRead3);
                        if (read == -1) break;
                        bytesRead3 += read;
                    }
                    
                    process3.waitFor();
                    
                    if (bytesRead3 == 6) {
                        mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                                buffer3[0], buffer3[1], buffer3[2], buffer3[3], buffer3[4], buffer3[5]);
                        readSuccess = true;
                    } else {
                        errorMsg = "Second path read only " + bytesRead3 + " bytes";
                    }
                }

            } catch (Exception e) {
                errorMsg = e.getMessage();
                e.printStackTrace();
            }

            final boolean success = readSuccess;
            final String finalMac = mac;
            final String finalError = errorMsg;

            mainHandler.post(() -> {
                if (!success || finalMac == null) {
                    tvBtResult.setText("Bluetooth Mac:\nREAD FAILED!\n\n" +
                        "Error: " + finalError + "\n\n" +
                        "Tried reading:\n" +
                        "/data/nvram/APCFG/APRDEB/BT_ADDR\n" +
                        "/data/nvdata/APCFG/APRDEB/BT_ADDR\n\n" +
                        "Make sure:\n" +
                        "1. File exists\n" +
                        "2. Root permissions granted\n" +
                        "3. File permissions are correct");
                    btnWriteBt.setEnabled(false);
                    btnCopyBt.setEnabled(false);
                    return;
                }

                currentBtMac = finalMac;
                tvBtResult.setText("Read Bluetooth Mac:\n" + finalMac);
                btnWriteBt.setEnabled(true);
                btnCopyBt.setEnabled(true);
                Toast.makeText(this, "Bluetooth MAC read: " + finalMac, Toast.LENGTH_SHORT).show();
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
            boolean success = false;
            String errorMsg = "";

            try {
                // Prvo pročitaj ceo fajl
                Process readProcess = Runtime.getRuntime().exec("su");
                DataOutputStream readOs = new DataOutputStream(readProcess.getOutputStream());
                readOs.writeBytes("cat /data/nvram/APCFG/APRDEB/BT_ADDR 2>/dev/null\n");
                readOs.writeBytes("exit\n");
                readOs.flush();

                DataInputStream dis = new DataInputStream(readProcess.getInputStream());
                byte[] fileContent = new byte[512];
                int totalBytes = 0;
                
                while (totalBytes < fileContent.length) {
                    int read = dis.read(fileContent, totalBytes, fileContent.length - totalBytes);
                    if (read == -1) break;
                    totalBytes += read;
                }
                
                readProcess.waitFor();

                if (totalBytes < 6) {
                    errorMsg = "File too small: " + totalBytes + " bytes";
                    mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show());
                    return;
                }

                // Zameni prvih 6 bajtova
                fileContent[0] = hexToByte(b[0]);
                fileContent[1] = hexToByte(b[1]);
                fileContent[2] = hexToByte(b[2]);
                fileContent[3] = hexToByte(b[3]);
                fileContent[4] = hexToByte(b[4]);
                fileContent[5] = hexToByte(b[5]);

                // Sačuvaj u lokalni fajl
                String destPath = getFilesDir().getAbsolutePath() + "/BT_ADDR";
                try (FileOutputStream file = new FileOutputStream(destPath)) {
                    file.write(fileContent, 0, totalBytes);
                }

                // Root komande za upis
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

            } catch (Exception e) {
                errorMsg = e.getMessage();
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
