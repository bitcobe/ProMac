package com.example.promac;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {

    private TextView tvWifiResult, tvBtResult;
    private EditText etWifiMac, etBtMac;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvWifiResult = findViewById(R.id.tvWifiResult);
        tvBtResult = findViewById(R.id.tvBtResult);
        etWifiMac = findViewById(R.id.etWifiMac);
        etBtMac = findViewById(R.id.etBtMac);

        Button btnReadWifi = findViewById(R.id.btnReadWifi);
        Button btnWriteWifi = findViewById(R.id.btnWriteWifi);
        Button btnReadBt = findViewById(R.id.btnReadBt);
        Button btnWriteBt = findViewById(R.id.btnWriteBt);

        btnReadWifi.setOnClickListener(v -> readMacAddress(true));
        btnReadBt.setOnClickListener(v -> readMacAddress(false));

        btnWriteWifi.setOnClickListener(v -> writeMacAddress(true));
        btnWriteBt.setOnClickListener(v -> writeMacAddress(false));
    }

    private void readMacAddress(boolean isWifi) {
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        TextView targetView = isWifi ? tvWifiResult : tvBtResult;

        String[] possiblePaths = {
            "/nvdata/APCFG/APRDEB/" + fileName,
            "/data/nvram/APCFG/APRDEB/" + fileName,
            "/vendor/nvdata/APCFG/APRDEB/" + fileName
        };

        String foundPath = null;
        byte[] bytes = null;

        for (String path : possiblePaths) {
            bytes = readBytesFromPath(path);
            if (bytes != null && bytes.length > 0) {
                foundPath = path;
                break;
            }
        }

        if (foundPath == null || bytes == null) {
            targetView.setText("Error: File " + fileName + " not found or unreadable.\nCheck Root (SU/Magisk) access.");
            return;
        }

        String mac = parseMacFromBytes(bytes, isWifi);
        String type = isWifi ? "Wi-Fi" : "Bluetooth";
        targetView.setText(type + " MAC Address:\n" + mac + "\n\nPath:\n" + foundPath);
    }

    private void writeMacAddress(boolean isWifi) {
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        EditText targetEditText = isWifi ? etWifiMac : etBtMac;
        String rawMac = targetEditText.getText().toString().trim();

        if (!isValidMac(rawMac)) {
            Toast.makeText(this, "Invalid MAC format! Use XX:XX:XX:XX:XX:XX", Toast.LENGTH_LONG).show();
            return;
        }

        byte[] macBytes = parseMacToBytes(rawMac);
        if (macBytes == null) {
            Toast.makeText(this, "Error parsing MAC address.", Toast.LENGTH_SHORT).show();
            return;
        }

        String primaryPath = "/nvdata/APCFG/APRDEB/" + fileName;
        String secondaryPath = "/data/nvram/APCFG/APRDEB/" + fileName;

        boolean successPrimary = writeMacToMtkFile(primaryPath, macBytes, isWifi);
        boolean successSecondary = writeMacToMtkFile(secondaryPath, macBytes, isWifi);

        if (successPrimary || successSecondary) {
            Toast.makeText(this, (isWifi ? "Wi-Fi" : "Bluetooth") + " MAC written successfully!", Toast.LENGTH_LONG).show();
            readMacAddress(isWifi);
        } else {
            Toast.makeText(this, "Failed to write MAC. Ensure root access.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean writeMacToMtkFile(String filePath, byte[] macBytes, boolean isWifi) {
        byte[] currentBytes = readBytesFromPath(filePath);
        if (currentBytes == null) {
            currentBytes = new byte[isWifi ? 16 : 6];
        }

        int offset = isWifi ? 4 : 0;
        if (currentBytes.length < offset + 6) {
            byte[] expanded = new byte[offset + 6];
            System.arraycopy(currentBytes, 0, expanded, 0, currentBytes.length);
            currentBytes = expanded;
        }

        System.arraycopy(macBytes, 0, currentBytes, offset, 6);

        StringBuilder hexString = new StringBuilder();
        for (byte b : currentBytes) {
            hexString.append(String.format("\\x%02X", b));
        }

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("printf '" + hexString.toString() + "' > " + filePath + "\n");
            os.writeBytes("chmod 0660 " + filePath + "\n");
            os.writeBytes("chown system:system " + filePath + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
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
}
