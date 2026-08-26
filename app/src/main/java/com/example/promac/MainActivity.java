package com.example.promac;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private TextView tvWifiResult, tvBtResult;
    private EditText etWifiMac, etBtMac;
    private Button btnWriteWifi, btnWriteBt;
    private Button btnCopyWifi, btnCopyBt;

    private String currentWifiMac = "";
    private String currentBtMac = "";
    private String wifiDestPath = "";
    private String btDestPath = "";

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

        // Inicijalizacija putanja
        wifiDestPath = getBaseContext().getFilesDir().getAbsolutePath() + "/WIFI";
        btDestPath = getBaseContext().getFilesDir().getAbsolutePath() + "/BT_ADDR";

        // Auto formatting sa ':' za polja za unos
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
        
        // Postavljanje lokalno administrirane unicast adrese
        macBytes[0] = (byte) ((macBytes[0] & 0xFE) | 0x02);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(String.format("%02X", macBytes[i]));
            if (i < 5) sb.append(":");
        }
        return sb.toString();
    }

    // ==================== ČITANJE MAC ADRESE ====================
    private void readMacAddress(boolean isWifi) {
        String fileName = isWifi ? "WIFI" : "BT_ADDR";
        String destPath = isWifi ? wifiDestPath : btDestPath;
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;
        int offset = isWifi ? 4 : 0; // Wi-Fi: pozicija 4, Bluetooth: pozicija 0

        // Kopiranje fajla na lokalnu lokaciju (kao u ChameleMAC)
        ArrayList<String> cmds = new ArrayList<>();
        cmds.add("cp -rp /data/nvram/APCFG/APRDEB/" + fileName + " " + destPath);
        cmds.add("chmod 0777 " + destPath);
        executeRootCommands(cmds);

        // Čitanje fajla
        File localFile = new File(destPath);
        byte[] fileContent = new byte[512];
        boolean readSuccess = false;

        if (localFile.exists()) {
            try (FileInputStream fin = new FileInputStream(localFile)) {
                int bytesRead = fin.read(fileContent);
                if (bytesRead >= offset + 6) {
                    readSuccess = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (!readSuccess) {
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

        // Parsiranje MAC adrese (kao u ChameleMAC)
        String mac = String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                fileContent[offset], fileContent[offset + 1], 
                fileContent[offset + 2], fileContent[offset + 3],
                fileContent[offset + 4], fileContent[offset + 5]);

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
    }

    // ==================== PISANJE MAC ADRESE ====================
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
            .setPositiveButton("Yes", (dialog, which) -> writeMacAddress(isWifi, rawMac))
            .setNegativeButton("No", null)
            .show();
    }

    private void writeMacAddress(boolean isWifi, String rawMac) {
        String fileName = isWifi ? "WIFI" : "BT_ADDR";
        String destPath = isWifi ? wifiDestPath : btDestPath;
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;
        int offset = isWifi ? 4 : 0; // Wi-Fi: pozicija 4, Bluetooth: pozicija 0

        String[] b = rawMac.split(":");
        byte[] fileContent = new byte[512];

        // Čitanje postojećeg fajla (kao u ChameleMAC)
        try (FileInputStream fin = new FileInputStream(destPath)) {
            fin.read(fileContent);
        } catch (IOException e) {
            Toast.makeText(this, "Error reading file!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Menjanje MAC adrese (kao u ChameleMAC)
        fileContent[offset] = hexToByte(b[0]);
        fileContent[offset + 1] = hexToByte(b[1]);
        fileContent[offset + 2] = hexToByte(b[2]);
        fileContent[offset + 3] = hexToByte(b[3]);
        fileContent[offset + 4] = hexToByte(b[4]);
        fileContent[offset + 5] = hexToByte(b[5]);

        // Pisanje fajla (kao u ChameleMAC - koristi FileOutputStream, NE printf)
        try (FileOutputStream file = new FileOutputStream(destPath)) {
            file.write(fileContent);
        } catch (IOException e) {
            Toast.makeText(this, "Error writing file!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Isključivanje Wi-Fi/Bluetooth pre promene
        boolean wifiEnabled = false;
        if (isWifi) {
            try {
                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifi != null && wifi.isWifiEnabled()) {
                    wifiEnabled = true;
                    wifi.setWifiEnabled(false);
                }
            } catch (Exception ignored) {}
        }

        // Root komande za vraćanje fajla (KAO U ChameleMAC - SAMO /data/nvram/, NE /nvdata/)
        ArrayList<String> cmds = new ArrayList<>();
        cmds.add("cp -rp " + destPath + " /data/nvram/APCFG/APRDEB/" + fileName);
        cmds.add("chmod 660 /data/nvram/APCFG/APRDEB/" + fileName);
        cmds.add("chown root.nvram /data/nvram/APCFG/APRDEB/" + fileName);
        
        // Dodatno: sinhronizacija da bi se promene sačuvale
        cmds.add("sync");

        boolean success = executeRootCommands(cmds);

        // Ponovno uključivanje Wi-Fi/Bluetooth
        if (isWifi && wifiEnabled) {
            try {
                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    wifi.setWifiEnabled(true);
                }
            } catch (Exception ignored) {}
        }

        if (success) {
            Toast.makeText(this, (isWifi ? "Wi-Fi" : "Bluetooth") + " MAC written successfully!\nReboot for changes to take effect.", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Failed to write MAC. Ensure root access.", Toast.LENGTH_LONG).show();
        }
    }

    // ==================== POMOĆNE METODE ====================

    private byte hexToByte(String s) {
        return (byte) ((Character.digit(s.charAt(0), 16) << 4) + Character.digit(s.charAt(1), 16));
    }

    private boolean executeRootCommands(ArrayList<String> cmds) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            for (String cmd : cmds) {
                os.writeBytes(cmd + "\n");
            }
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
        return mac != null && mac.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
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
}
