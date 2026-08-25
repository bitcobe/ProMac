package com.example.promac;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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
        String foundPath = findMtkFilePath(fileName);

        if (foundPath == null) {
            tvResult.setText("Greška: Fajl " + fileName + " nije pronađen.\n\nProverite da li je telefon rutovan (Magisk/SU) i da li ste odobrili Root pristup aplikaciji.");
            return;
        }

        byte[] bytes = readBytesFromPath(foundPath);
        if (bytes == null || bytes.length == 0) {
            tvResult.setText("Greška pri čitanju fajla na putanji:\n" + foundPath + "\n\nObezbedite Root dozvolu.");
            return;
        }

        String mac = parseMacFromBytes(bytes, isWifi);
        String type = isWifi ? "Wi-Fi" : "Bluetooth";
        tvResult.setText(type + " MAC Adresa:\n" + mac + "\n\nPutanja:\n" + foundPath);
    }

    /**
     * Pronalazi putanju fajla prvenstveno proveravajući standardno, 
     * a ako ne uspe, koristi Root (su) test.
     */
    private String findMtkFilePath(String fileName) {
        String[] possiblePaths = {
            "/nvdata/APCFG/APRDEB/" + fileName,
            "/data/nvram/APCFG/APRDEB/" + fileName,
            "/vendor/nvdata/APCFG/APRDEB/" + fileName
        };

        // 1. Provera standardnim putem
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists()) return path;
        }

        // 2. Provera preko Root 'su' komande ako sistem skriva fajl
        for (String path : possiblePaths) {
            if (checkFileExistsWithRoot(path)) {
                return path;
            }
        }

        return null;
    }

    private boolean checkFileExistsWithRoot(String path) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "[ -f " + path + " ] && echo 1 || echo 0"});
            InputStream is = process.getInputStream();
            int result = is.read();
            process.waitFor();
            return result == '1';
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] readBytesFromPath(String filePath) {
        File file = new File(filePath);
        if (file.canRead()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                return data;
            } catch (IOException ignored) {}
        }
        return readBytesWithRoot(filePath);
    }

    private byte[] readBytesWithRoot(String filePath) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat " + filePath});
            InputStream is = process.getInputStream();
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
}
