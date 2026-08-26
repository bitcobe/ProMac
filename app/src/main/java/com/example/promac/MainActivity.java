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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvWifiResult, tvBtResult;
    private EditText etWifiMac, etBtMac;
    private Button btnWriteWifi, btnWriteBt;
    private Button btnCopyWifi, btnCopyBt;

    private String currentWifiMac = "";
    private String currentBtMac = "";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWifiResult = findViewById(R.id.tvWifiResult);
        tvBtResult = findViewById(R.id.tvBtResult);
        etWifiMac = findViewById(R.id.etWifiMac);
        etBtMac = findViewById(R.id.etBtMac);

        Button btnReadWifi = findViewById(R.id.btnReadWifi);
        btnWriteWifi = findViewById(R.id.btnWriteWifi);
        Button btnGenerateWifi = findViewById(R.id.btnGenerateWifi);
        btnCopyWifi = findViewById(R.id.btnCopyWifi);

        Button btnReadBt = findViewById(R.id.btnReadBt);
        btnWriteBt = findViewById(R.id.btnWriteBt);
        Button btnGenerateBt = findViewById(R.id.btnGenerateBt);
        btnCopyBt = findViewById(R.id.btnCopyBt);

        setupMacFormatting(etWifiMac);
        setupMacFormatting(etBtMac);

        btnReadWifi.setOnClickListener(v -> readMacAddress(true));
        btnReadBt.setOnClickListener(v -> readMacAddress(false));

        btnWriteWifi.setOnClickListener(v -> confirmWriteMacAddress(true));
        btnWriteBt.setOnClickListener(v -> confirmWriteMacAddress(false));

        btnGenerateWifi.setOnClickListener(v -> etWifiMac.setText(generateRandomMac()));
        btnGenerateBt.setOnClickListener(v -> etBtMac.setText(generateRandomMac()));

        btnCopyWifi.setOnClickListener(v -> copyToClipboard("Wi-Fi MAC", currentWifiMac));
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

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(String.format("%02X", macBytes[i]));
            if (i < 5) sb.append(":");
        }
        return sb.toString();
    }

    private void readMacAddress(boolean isWifi) {
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;
        targetView.setText("Reading...");

        executor.execute(() -> {
            String fileName = isWifi ? "WIFI" : "BT_Addr";
            String[] possiblePaths = {
                "/nvdata/APCFG/APRDEB/" + fileName,
                "/data/nvram/APCFG/APRDEB/" + fileName,
                "/vendor/nvdata/APCFG/APRDEB/" + fileName
            };

            byte[] bytes = null;
            for (String path : possiblePaths) {
                bytes = readBytesFromPath(path);
                if (bytes != null && bytes.length > 0) break;
            }

            final byte[] finalBytes = bytes;
            mainHandler.post(() -> {
                if (finalBytes == null) {
                    targetView.setText((isWifi ? "WiFi Mac:\n" : "Bluetooth Mac:\n") + "Error reading file");
                    if (isWifi) {
                        btnWriteWifi.setEnabled(false);
                        btnCopyWifi.setEnabled(false);
                    } else {
                        btnWriteBt.setEnabled(false);
                        btnCopyBt.setEnabled(false);
                    }
                    return;
                }

                String mac = parseMacFromBytes(finalBytes, isWifi);
                if (isWifi) {
                    currentWifiMac = mac;
                    targetView.setText("Read WiFi Mac:\n" + mac);
                    btnWriteWifi.setEnabled(true);
                    btnCopyWifi.setEnabled(true);
                } else {
                    currentBtMac = mac;
                    targetView.setText("Read Bluetooth Mac:\n" + mac);
                    btnWriteBt.setEnabled(true);
                    btnCopyBt.setEnabled(true);
                }
            });
        });
    }

    private void confirmWriteMacAddress(boolean isWifi) {
        EditText targetEditText = isWifi ? etWifiMac : etBtMac;
        String rawMac = targetEditText.getText().toString().trim();

        if (!isValidMac(rawMac)) {
            Toast.makeText(this, "Invalid MAC format!", Toast.LENGTH_LONG).show();
            return;
        }

        String type = isWifi ? "Wi-Fi" : "Bluetooth";

        new AlertDialog.Builder(this)
            .setTitle("Confirm Write")
            .setMessage("Are you sure you want to write " + rawMac + " as the new " + type + " MAC address?")
            .setPositiveButton("Yes", (dialog, which) -> startWriteProcess(isWifi, rawMac))
            .setNegativeButton("No", null)
            .show();
    }

    private void startWriteProcess(boolean isWifi, String rawMac) {
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;
        Toast.makeText(this, "Writing MAC address...", Toast.LENGTH_SHORT).show();

        executor.execute(() -> {
            byte[] macBytes = parseMacToBytes(rawMac);
            if (macBytes == null) {
                mainHandler.post(() -> Toast.makeText(MainActivity.this, "Error parsing MAC address.", Toast.LENGTH_SHORT).show());
                return;
            }

            boolean isSuccess = writeMacChameleStylePersistent(isWifi, macBytes);

            mainHandler.post(() -> {
                if (isSuccess) {
                    Toast.makeText(MainActivity.this, (isWifi ? "Wi-Fi" : "Bluetooth") + " MAC written successfully!", Toast.LENGTH_LONG).show();
                    if (isWifi) {
                        currentWifiMac = rawMac;
                        targetView.setText("Write WiFi Mac:\n" + rawMac);
                        btnCopyWifi.setEnabled(true);
                    } else {
                        currentBtMac = rawMac;
                        targetView.setText("Write Bluetooth Mac:\n" + rawMac);
                        btnCopyBt.setEnabled(true);
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Failed to write MAC. Verify Root.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private boolean writeMacChameleStylePersistent(boolean isWifi, byte[] newMacBytes) {
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        
        String targetNVRAM = "/data/nvram/APCFG/APRDEB/" + fileName;
        String targetNVDATA = "/nvdata/APCFG/APRDEB/" + fileName;
        String targetVendor = "/vendor/nvdata/APCFG/APRDEB/" + fileName;

        File localFile = new File(getFilesDir(), fileName);
        String tempPath = localFile.getAbsolutePath();

        // 1. Učitavanje postojecih bajtova iz NVRAM-a
        String pullCmd = "cp -rp " + targetNVRAM + " " + tempPath + "\n" +
                         "chmod 0777 " + tempPath + "\n";
        
        if (!runRootScript(pullCmd)) {
            pullCmd = "cp -rp " + targetNVDATA + " " + tempPath + "\n" +
                      "chmod 0777 " + tempPath + "\n";
            runRootScript(pullCmd);
        }

        if (!localFile.exists()) {
            return false;
        }

        // 2. Modifikacija bajtova u memoriji (Java sloj)
        byte[] fileContent = new byte[(int) localFile.length()];
        if (fileContent.length == 0) fileContent = new byte[512];

        try (FileInputStream fin = new FileInputStream(localFile)) {
            fin.read(fileContent);
        } catch (IOException e) {
            return false;
        }

        int offset = isWifi ? 4 : 0;
        for (int i = 0; i < 6; i++) {
            fileContent[offset + i] = newMacBytes[i];
        }

        try (FileOutputStream fout = new FileOutputStream(localFile)) {
            fout.write(fileContent);
            fout.flush();
        } catch (IOException e) {
            return false;
        }

        // 3. Gašenje Wi-Fi modul pre pisanja
        WifiManager wifiManager = null;
        boolean wasEnabled = false;
        if (isWifi) {
            try {
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    wasEnabled = true;
                    wifiManager.setWifiEnabled(false);
                    Thread.sleep(1000);
                }
            } catch (Exception ignored) {}
        }

        // 4. Sinhronizovani upis na sve lokacije + brisanje nvram_daemon keša
        StringBuilder pushCmd = new StringBuilder();

        pushCmd.append("mount -o remount,rw /nvdata 2>/dev/null\n");
        pushCmd.append("mount -o remount,rw /vendor/nvdata 2>/dev/null\n");
        pushCmd.append("mount -o remount,rw /data 2>/dev/null\n");

        String[] targetPaths = {targetNVRAM, targetNVDATA, targetVendor};
        for (String path : targetPaths) {
            pushCmd.append("cp -f ").append(tempPath).append(" ").append(path).append(" 2>/dev/null\n");
            pushCmd.append("chmod 660 ").append(path).append(" 2>/dev/null\n");
            pushCmd.append("chown root.nvram ").append(path).append(" 2>/dev/null || chown system.system ").append(path).append(" 2>/dev/null\n");
            pushCmd.append("restorecon -F ").append(path).append(" 2>/dev/null\n");
        }

        // Brisanje povratnog keša koji spriječava zamenjivanje stare adrese nakon restarta
        pushCmd.append("rm -f /data/nvram/media/WIFI* 2>/dev/null\n");
        pushCmd.append("rm -f /nvdata/media/WIFI* 2>/dev/null\n");
        pushCmd.append("sync\n");

        boolean res = runRootScript(pushCmd.toString());

        // 5. Paljenje Wi-Fi modula
        if (isWifi && wasEnabled && wifiManager != null) {
            try {
                wifiManager.setWifiEnabled(true);
            } catch (Exception ignored) {}
        }

        return res;
    }

    private boolean runRootScript(String script) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(script);
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (process != null) process.destroy();
            } catch (Exception ignored) {}
        }
    }

    private boolean isValidMac(String mac) {
        return mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    }

    private byte[] parseMacToBytes(String mac) {
        String[] parts = mac.split("[:-]");
        if (parts.length != 6) return null;
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return bytes;
    }

    private byte[] readBytesFromPath(String filePath) {
        File file = new File(filePath);
        if (file.exists() && file.canRead()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int read = fis.read(data);
                if (read > 0) return data;
            } catch (IOException ignored) {}
        }
        return readBytesWithRoot(filePath);
    }

    private byte[] readBytesWithRoot(String filePath) {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            InputStream is = process.getInputStream();

            os.writeBytes("cat " + filePath + "\n");
            os.writeBytes("exit\n");
            os.flush();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[256];
            int bytesRead;

            while ((bytesRead = is.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }

            process.waitFor();
            byte[] data = buffer.toByteArray();
            return data.length > 0 ? data : null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String parseMacFromBytes(byte[] data, boolean isWifi) {
        if (data == null) return "Empty File";
        int offset = 0;
        int length = 6;

        if (isWifi) {
            if (data.length >= 10) offset = 4;
            else if (data.length >= 6) offset = 0;
            else return "Invalid WIFI file size";
        } else {
            if (data.length < 6) return "Invalid BT_Addr file size";
            offset = 0;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[offset + i]));
            if (i < length - 1) sb.append(":");
        }
        return sb.toString();
    }

    private void copyToClipboard(String label, String text) {
        if (text == null || text.isEmpty()) {
            Toast.makeText(this, "No MAC address to copy!", Toast.LENGTH_SHORT).show();
            return;
        }
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
