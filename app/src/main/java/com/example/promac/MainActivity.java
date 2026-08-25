package com.example.promac;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private TextView tvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvResult = findViewById(R.id.tvResult);
        Button btnReadWifi = findViewById(R.id.btnReadWifi);
        Button btnReadBt = findViewById(R.id.btnReadBt);

        btnReadWifi.setOnClickListener(v -> readMacAddress(true));
        btnReadBt.setOnClickListener(v -> readMacAddress(false));
    }

    private void readMacAddress(boolean isWifi) {
        String fileName = isWifi ? "WIFI" : "BT_Addr";
        File fileToRead = findMtkFile(fileName);

        if (fileToRead == null || !fileToRead.exists()) {
            tvResult.setText("Greška: Fajl " + fileName + " nije pronađen.\n(Proverite Root pristup)");
            return;
        }

        try {
            byte[] bytes = readFileToByteArray(fileToRead);
            String mac = parseMacFromBytes(bytes, isWifi);
            String type = isWifi ? "Wi-Fi" : "Bluetooth";
            tvResult.setText(type + " MAC Adresa:\n" + mac + "\n\nPutanja:\n" + fileToRead.getAbsolutePath());
        } catch (IOException e) {
            tvResult.setText("Greška pri čitanju fajla (Permission Denied). Obezbedite Root.");
        }
    }

    private File findMtkFile(String fileName) {
        String[] possiblePaths = {
            "/nvdata/APCFG/APRDEB/" + fileName,
            "/data/nvram/APCFG/APRDEB/" + fileName,
            "/vendor/nvdata/APCFG/APRDEB/" + fileName
        };

        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        return null;
    }

    private String parseMacFromBytes(byte[] data, boolean isWifi) {
        if (data == null) return "Prazan fajl";
        int offset = 0;
        int length = 6;

        if (isWifi) {
            if (data.length >= 10) offset = 4;
            else if (data.length >= 6) offset = 0;
            else return "Neispravna veličina WIFI fajla";
        } else {
            if (data.length < 6) return "Neispravna veličina BT_Addr fajla";
            offset = 0;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(String.format("%02X", data[offset + i]));
            if (i < length - 1) sb.append(":");
        }
        return sb.toString();
    }

    private byte[] readFileToByteArray(File file) throws IOException {
        if (file.canRead()) {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            return data;
        } else {
            return readBytesWithRoot(file.getAbsolutePath());
        }
    }

    private byte[] readBytesWithRoot(String filePath) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + filePath});
            java.io.InputStream is = process.getInputStream();
            byte[] buffer = new byte[256];
            int bytesRead = is.read(buffer);
            process.waitFor();

            if (bytesRead > 0) {
                byte[] data = new byte[bytesRead];
                System.arraycopy(buffer, 0, data, 0, bytesRead);
                return data;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
