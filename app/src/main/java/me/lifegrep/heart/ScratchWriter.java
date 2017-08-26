/**
 * Lifegrep scratch writer
 * Heartbeats and daily events are written in real time to a json scratch file in internal storage.
 * After sometime, these events/heartbeats are uploaded to a server and the scratch cleaned.
 */
package me.lifegrep.heart;

import android.content.Context;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;


public class ScratchWriter {

    Context context;
    String filename;

    public ScratchWriter(Context context, String filename) {
        this.context = context;
        //this.filename = context.getFilesDir().getPath() + "/" + filename;
        this.filename = Environment.getExternalStorageDirectory().getPath()+"/"+filename;
    }


    public void saveData(String data) {
        FileWriter fw = null;
        BufferedWriter bw = null;
        try {
            fw = new FileWriter(filename, true);
            bw = new BufferedWriter(fw);
            bw.write(data + "\n");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null)
                    bw.close();
                if (fw != null)
                    fw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }


    public String getData() {
        try {
            File f = new File(filename);
            FileInputStream inStream = new FileInputStream(f);
            int size = inStream.available();
            byte[] buffer = new byte[size];
            inStream.read(buffer);
            inStream.close();
            return new String(buffer);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public void eraseContents() {
        FileWriter fw = null;
        try {
            fw = new FileWriter(filename);
            fw.write("");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fw != null)
                    fw.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

}
