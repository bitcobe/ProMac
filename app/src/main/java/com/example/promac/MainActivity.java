package com.example.macspoofer;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.security.SecureRandom;

public class MainActivity extends Activity {

    private EditText etMacAddress;
    private Button btnApplyMac, btnGenerateRandom;
    
    // Putanja do MediaTek Wi-Fi NVRAM fajla
    private static final String MTK_NVRAM_WIFI_PATH = "/data/nvram/APCFG/APRDEB/WIFI";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etMacAddress = findViewById(R.id.etMacAddress);
        btnApplyMac = findViewById(R.id.btnApplyMac);
        btnGenerateRandom = findViewById(R.id.btnGenerateRandom);

        // Provera da li je uređaj MediaTek
        if (!isMediaTekDevice()) {
            Toast.makeText(this, "Upozorenje: Čipset nije MediaTek! NVRAM izmena možda neće raditi.", Toast.LENGTH_LONG).show();
        }

        // Učitavanje trenutne adrese iz NVRAM-a pri pokretanju
        String currentNvramMac = readMacFromNvram();
        if (currentNvramMac != null) {
            etMacAddress.setText(currentNvramMac);
        }

        // Generisanje nasumične MAC adrese
        btnGenerateRandom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                etMacAddress.setText(generateRandomMac());
            }
        });

        // Primenjivanje nove trajne MAC adrese
        btnApplyMac.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newMac = etMacAddress.getText().toString().trim().toUpperCase();
                
                if (!validateMac(newMac)) {
                    Toast.makeText(MainActivity.this, "Neispravan format MAC adrese!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (applyPermanentMacMediaTek(newMac)) {
                    Toast.makeText(MainActivity.this, "MAC adresa uspešno i trajno izmenjena!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, "Greška pri upisivanju u NVRAM (proverite Root pristup).", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Proverava da li uređaj pokreće MediaTek procesor
     */
    private boolean isMediaTekDevice() {
        String hardware = Build.HARDWARE.toLowerCase();
        return hardware.startsWith("mt") || new File(MTK_NVRAM_WIFI_PATH).exists();
    }

    /**
     * Generiše nasumičnu validnu MAC adresu (sufiks sa 'AA' radi izbegavanja konflikta)
     */
    private String generateRandomMac() {
        SecureRandom random = new SecureRandom();
        byte[] macBytes = new byte[5];
        random.nextBytes(macBytes);
        
        StringBuilder sb = new StringBuilder("AA");
        for (byte b : macBytes) {
            sb.append(String.format(":%02X", b));
        }
        return sb.toString();
    }

    /**
     * Validacija MAC adrese (Format XX:XX:XX:XX:XX:XX)
     */
    private boolean validateMac(String mac) {
        if (mac == null || mac.length() != 17) return false;
        return mac.matches("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    }

    /**
     * Čita trenutnu MAC adresu iz bajtova 4-9 NVRAM fajla
     */
    private String readMacFromNvram() {
        if (!new File(MTK_NVRAM_WIFI_PATH).exists()) return null;
        
        try {
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(MTK_NVRAM_WIFI_PATH, "r");
            byte[] bytes = new byte[10];
            raf.readFully(bytes);
            raf.close();

            return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    bytes[4], bytes[5], bytes[6], bytes[7], bytes[8], bytes[9]);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Glavni mehanizam za trajno menjanje MAC adrese u NVRAM-u
     */
    private boolean applyPermanentMacMediaTek(String newMac) {
        String[] hexParts = newMac.split(":");
        if (hexParts.length != 6) return false;

        byte[] macBytes = new byte[6];
        for (int i = 0; i < 6; i++) {
            macBytes[i] = (byte) Integer.parseInt(hexParts[i], 16);
        }

        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        boolean wasWifiEnabled = false;

        // 1. Gašenje Wi-Fi-ja radi oslobađanja drajvera
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            wasWifiEnabled = true;
            wifiManager.setWifiEnabled(false);
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        }

        // 2. Priprema Root komandi za direct-patching bajtova 4-9
        StringBuilder cmdBuilder = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int byteVal = macBytes[i] & 0xFF;
            int offset = 4 + i; // Od bajta 4 do bajta 9
            cmdBuilder.append(String.format("printf '\\x%02X' | dd of=%s bs=1 seek=%d conv=notrunc\n", 
                    byteVal, MTK_NVRAM_WIFI_PATH, offset));
        }

        // 3. Postavljanje dozvola, vlasništva i SELinux konteksta
        cmdBuilder.append("chmod 660 ").append(MTK_NVRAM_WIFI_PATH).append("\n");
        cmdBuilder.append("chown root.nvram ").append(MTK_NVRAM_WIFI_PATH).append("\n");
        cmdBuilder.append("restorecon ").append(MTK_NVRAM_WIFI_PATH).append("\n");

        // 4. Izvršavanje kroz Root Shell
        boolean result = runRootScript(cmdBuilder.toString());

        // 5. Ponovno uključivanje Wi-Fi mreže sa novom adresom
        if (wasWifiEnabled && wifiManager != null) {
            wifiManager.setWifiEnabled(true);
        }

        return result;
    }

    /**
     * Pomoćna metoda za izvršavanje komandi sa Root privilegijama
     */
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
}
