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

    private static final int BT_FILE_SIZE_WITH_CHECKSUM = 66;
    private static final int BT_FILE_SIZE_WITHOUT_CHECKSUM = 64;
    private static final int MAC_LEN = 6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWifiResult = findViewById(R.id.tvWifiResult);
        etWifiMac = findViewById(R.id.etWifiMac);
        cbGenOnReset = findViewById(R.id.cbGenOnReset);
        Button btnReadWifi = findViewById(R.id.btnReadWifi);
        btnWriteWifi = findViewById(R.id.btnWriteWifi);
        Button btnGenerateWifi = findViewById(R.id.btnGenerateWifi);
        btnCopyWifi = findViewById(R.id.btnCopyWifi);

        tvBtResult = findViewById(R.id.tvBtResult);
        etBtMac = findViewById(R.id.etBtMac);
        Button btnReadBt = findViewById(R.id.btnReadBt);
        btnWriteBt = findViewById(R.id.btnWriteBt);
        Button btnGenerateBt = findViewById(R.id.btnGenerateBt);
        btnCopyBt = findViewById(R.id.btnCopyBt);

        setupMacFormatting(etWifiMac);
        setupMacFormatting(etBtMac);

        btnReadWifi.setOnClickListener(v -> readMacAddress());
        btnWriteWifi.setOnClickListener(v -> confirmWriteMacAddress());
        btnGenerateWifi.setOnClickListener(v -> etWifiMac.setText(generateRandomMac()));
        btnCopyWifi.setOnClickListener(v -> copyToClipboard("Wi-Fi MAC", currentWifiMac));

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
                    if (read >= 10) readSuccess = true;
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

            mainHandler.post(() -> {
                tvWifiResult.setText("Write WiFi Mac:\n" + rawMac);
                Toast.makeText(MainActivity.this, "Wi-Fi MAC address changed successfully.", Toast.LENGTH_LONG).show();
            });
        });
    }

    // ==================== BLUETOOTH LOGIKA ====================
    
    private void readBtMacAddress() {
        tvBtResult.setText("Reading...");

        executor.execute(() -> {
            String fileName = "BT_Addr";
            String destPath = getFilesDir().getAbsolutePath() + "/" + fileName;

            // Kao i za Wi-Fi - kopiraj sa /data/nvram/
            ArrayList<String> cmds = new ArrayList<>();
            cmds.add("cp -rp /data/nvram/APCFG/APRDEB/" + fileName + " " + destPath);
            cmds.add("chmod 0777 " + destPath);
            executeRootCmds(cmds);

            File localFile = new File(destPath);
            byte[] fileContent = new byte[BT_FILE_SIZE_WITH_CHECKSUM];
            boolean readSuccess = false;

            if (localFile.exists()) {
                try (FileInputStream fin = new FileInputStream(localFile)) {
                    int bytesRead = fin.read(fileContent);
                    if (bytesRead >= MAC_LEN) {
                        readSuccess = true;
                    }
                } catch (IOException ignored) {}
            }

            final boolean success = readSuccess;
            final byte[] data = fileContent;

            mainHandler.post(() -> {
                if (!success) {
                    tvBtResult.setText("Bluetooth Mac:\nError reading file");
                    btnWriteBt.setEnabled(false);
                    btnCopyBt.setEnabled(false);
                    return;
                }

                // MAC adresa je na pozicijama 0-5 (prvih 6 bajtova)
                String mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                        data[0], data[1], data[2], data[3], data[4], data[5]);

                currentBtMac = mac;
                tvBtResult.setText("Read Bluetooth Mac:\n" + mac);
                btnWriteBt.setEnabled(true);
                btnCopyBt.setEnabled(true);
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
            String fileName = "BT_Addr";
            String destPath = getFilesDir().getAbsolutePath() + "/" + fileName;

            // 1. Pročitaj originalni fajl (66 bytes sa checksum) sa /data/nvram/
            File sourceFile = new File("/data/nvram/APCFG/APRDEB/" + fileName);
            byte[] originalData = new byte[BT_FILE_SIZE_WITH_CHECKSUM];
            int originalLength = 0;

            if (sourceFile.exists()) {
                try (FileInputStream fin = new FileInputStream(sourceFile)) {
                    originalLength = fin.read(originalData);
                } catch (IOException ignored) {}
            }

            // Ako nema fajla, kreiraj prazan (66 bytes)
            if (originalLength != BT_FILE_SIZE_WITH_CHECKSUM) {
                originalLength = BT_FILE_SIZE_WITH_CHECKSUM;
                for (int i = 0; i < originalLength; i++) {
                    originalData[i] = 0x00;
                }
            }

            // 2. Kreiraj 64-bajtni fajl BEZ checksum-a (skini poslednja 2 bajta)
            byte[] dataWithoutChecksum = new byte[BT_FILE_SIZE_WITHOUT_CHECKSUM];
            System.arraycopy(originalData, 0, dataWithoutChecksum, 0, BT_FILE_SIZE_WITHOUT_CHECKSUM);

            // 3. Zameni MAC adresu na početku (pozicije 0-5)
            dataWithoutChecksum[0] = hexToByte(b[0]);
            dataWithoutChecksum[1] = hexToByte(b[1]);
            dataWithoutChecksum[2] = hexToByte(b[2]);
            dataWithoutChecksum[3] = hexToByte(b[3]);
            dataWithoutChecksum[4] = hexToByte(b[4]);
            dataWithoutChecksum[5] = hexToByte(b[5]);

            // 4. Sačuvaj 64-bajtni fajl u /data/BT_Addr (bez checksum)
            String destPathNoChecksum = getFilesDir().getAbsolutePath() + "/" + fileName + "_no_crc";
            try (FileOutputStream file = new FileOutputStream(destPathNoChecksum)) {
                file.write(dataWithoutChecksum);
            } catch (IOException e) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error saving BT file.", Toast.LENGTH_SHORT).show());
                return;
            }

            // 5. Kopiraj 64-bajtni fajl na /data/BT_Addr (bez checksum, sistem će ga sam dopuniti)
            ArrayList<String> cmds = new ArrayList<>();
            cmds.add("cp -rp " + destPathNoChecksum + " /data/" + fileName);
            cmds.add("chmod 660 /data/" + fileName);
            cmds.add("chown root.nvram /data/" + fileName);

            // 6. Kopiraj ISTI 64-bajtni fajl na /data/nvram/ (bez checksum)
            cmds.add("cp -rp " + destPathNoChecksum + " /data/nvram/APCFG/APRDEB/" + fileName);
            cmds.add("chmod 660 /data/nvram/APCFG/APRDEB/" + fileName);
            cmds.add("chown root.nvram /data/nvram/APCFG/APRDEB/" + fileName);

            // 7. Kopiraj ISTI 64-bajtni fajl na /nvdata/ (bez checksum)
            cmds.add("cp -rp " + destPathNoChecksum + " /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null || true");
            cmds.add("chmod 660 /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null || true");
            cmds.add("chown root.nvram /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null || true");

            cmds.add("sync");
            executeRootCmds(cmds);

            // Obriši privremeni fajl
            new File(destPathNoChecksum).delete();

            mainHandler.post(() -> {
                tvBtResult.setText("Write Bluetooth Mac:\n" + rawMac);
                Toast.makeText(MainActivity.this,
                    "Bluetooth MAC changed to: " + rawMac + "\nReboot for changes to take effect.",
                    Toast.LENGTH_LONG).show();
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
