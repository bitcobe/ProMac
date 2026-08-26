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
        btDestPath = getBaseContext().getFilesDir().getAbsolutePath() + "/BT_Addr";

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

    // ==================== ČITANJE MAC ADRESE (VAŠ NAČIN - RADI ZA OBA) ====================
    private void readMacAddress(boolean isWifi) {
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;

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

        if (bytes == null) {
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

        String mac = parseMacFromBytes(bytes, isWifi);
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

    // ==================== PISANJE MAC ADRESE (NOVI NAČIN - CHAMELEMAC PRISTUP) ====================
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
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        String destPath = isWifi ? wifiDestPath : btDestPath;
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;
        int offset = isWifi ? 4 : 0; // Wi-Fi: pozicija 4, Bluetooth: pozicija 0

        String[] b = rawMac.split(":");
        
        // Prvo pročitamo ORIGINALNI fajl sa sistema (preko vaše metode)
        byte[] originalBytes = null;
        String[] possiblePaths = {
            "/nvdata/APCFG/APRDEB/" + fileName,
            "/data/nvram/APCFG/APRDEB/" + fileName,
            "/vendor/nvdata/APCFG/APRDEB/" + fileName
        };
        
        for (String path : possiblePaths) {
            originalBytes = readBytesFromPath(path);
            if (originalBytes != null && originalBytes.length > 0) break;
        }

        // Ako nismo uspeli da pročitamo, kreiramo novi fajl odgovarajuće veličine
        if (originalBytes == null) {
            originalBytes = new byte[isWifi ? 512 : 512]; // dovoljno veliko
        }

        // Kopiramo originalni sadržaj u novi niz
        byte[] fileContent = new byte[Math.max(originalBytes.length, offset + 6)];
        System.arraycopy(originalBytes, 0, fileContent, 0, originalBytes.length);

        // Menjamo MAC adresu na odgovarajućoj poziciji
        fileContent[offset] = hexToByte(b[0]);
        fileContent[offset + 1] = hexToByte(b[1]);
        fileContent[offset + 2] = hexToByte(b[2]);
        fileContent[offset + 3] = hexToByte(b[3]);
        fileContent[offset + 4] = hexToByte(b[4]);
        fileContent[offset + 5] = hexToByte(b[5]);

        // Čuvamo fajl na lokalnu putanju (FileOutputStream umesto printf)
        try (FileOutputStream file = new FileOutputStream(destPath)) {
            file.write(fileContent);
        } catch (IOException e) {
            Toast.makeText(this, "Error saving file!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Isključivanje Wi-Fi pre promene (samo za Wi-Fi)
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

        // Root komande za vraćanje fajla (ChameleMAC pristup)
        ArrayList<String> cmds = new ArrayList<>();
        
        // Kopiranje na /data/nvram/ (PRVA LOKACIJA)
        cmds.add("cp -rp " + destPath + " /data/nvram/APCFG/APRDEB/" + fileName);
        cmds.add("chmod 660 /data/nvram/APCFG/APRDEB/" + fileName);
        cmds.add("chown root.nvram /data/nvram/APCFG/APRDEB/" + fileName);
        
        // Kopiranje na /nvdata/ (DRUGA LOKACIJA)
        cmds.add("cp -rp " + destPath + " /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null");
        cmds.add("chmod 660 /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null");
        cmds.add("chown root.nvram /nvdata/APCFG/APRDEB/" + fileName + " 2>/dev/null");
        
        // Sinhronizacija da bi se promene sačuvale na fleš
        cmds.add("sync");

        boolean success = executeRootCommands(cmds);

        // Ponovno uključivanje Wi-Fi
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

    // ==================== VAŠE METODE ZA ČITANJE (OSTAVLJENE ISTE) ====================
    
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
