package com.pdv.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Sincronizacao local + FTP das fotos da Ordem de Servico.
 * Mantem a foto local como caminho mais rapido e usa FTP como copia remota.
 */
public class OSPhotoSyncManager {
    private static final String TAG = "OSPhotoSyncManager";
    private static final String REMOTE_BASE_DIR = "pdv_os_fotos";
    private static final int FTP_CONNECT_TIMEOUT_MS = 6000;
    private static final int FTP_DATA_TIMEOUT_MS = 12000;

    public static final String STATUS_PENDING = "pendente";
    public static final String STATUS_SYNCED = "sincronizada";
    public static final String STATUS_ERROR = "erro";

    private final Context context;

    public OSPhotoSyncManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public UploadResult uploadPhoto(int osId, String localPath) throws Exception {
        File file = new File(localPath);
        if (!file.exists() || file.length() == 0) {
            throw new IllegalStateException("Arquivo local da foto nao encontrado.");
        }

        String remotePath = buildRemotePath(osId, file.getName());
        FTPClient ftp = null;
        try {
            ftp = connect();
            ensureRemoteDirectory(ftp, getRemoteDirectory(osId));
            try (FileInputStream fis = new FileInputStream(file)) {
                boolean sent = ftp.storeFile(remotePath, fis);
                if (!sent) {
                    throw new IllegalStateException("FTP recusou o envio da foto.");
                }
            }

            long remoteSize = getRemoteSize(ftp, remotePath);
            if (remoteSize > 0 && remoteSize < file.length() * 0.9) {
                throw new IllegalStateException("Foto enviada incompleta ao FTP.");
            }
            return new UploadResult(remotePath, file.length());
        } finally {
            disconnectQuietly(ftp);
        }
    }

    public String ensureLocalCopy(OSPhoto photo) throws Exception {
        if (photo == null) return null;
        if (hasLocalFile(photo.localPath)) {
            return photo.localPath;
        }
        if (photo.ftpPath == null || photo.ftpPath.trim().isEmpty()) {
            return null;
        }
        if (!isNetworkAvailable()) {
            return null;
        }

        File local = buildLocalCacheFile(photo);
        if (local.exists() && local.length() > 0) {
            photo.localPath = local.getAbsolutePath();
            return photo.localPath;
        }

        FTPClient ftp = null;
        File tmp = new File(local.getAbsolutePath() + ".tmp");
        try {
            ftp = connect();
            File parent = local.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                boolean ok = ftp.retrieveFile(photo.ftpPath, fos);
                if (!ok) {
                    throw new IllegalStateException("Nao foi possivel baixar a foto do FTP.");
                }
            }
            if (!tmp.exists() || tmp.length() == 0) {
                throw new IllegalStateException("Foto baixada vazia do FTP.");
            }
            if (local.exists()) local.delete();
            boolean renamed = tmp.renameTo(local);
            if (!renamed) {
                throw new IllegalStateException("Nao foi possivel gravar cache local da foto.");
            }
            photo.localPath = local.getAbsolutePath();
            return photo.localPath;
        } finally {
            if (tmp.exists()) tmp.delete();
            disconnectQuietly(ftp);
        }
    }

    public Bitmap loadBitmap(OSPhoto photo) {
        if (photo == null) return null;
        String path = hasLocalFile(photo.localPath) ? photo.localPath : null;
        return path == null ? null : BitmapFactory.decodeFile(path);
    }

    public boolean hasLocalFile(String path) {
        if (path == null || path.trim().isEmpty()) return false;
        File file = new File(path);
        return file.exists() && file.length() > 0;
    }

    public boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            Log.w(TAG, "Falha ao verificar rede", e);
            return false;
        }
    }

    public String getDisplaySource(OSPhoto photo) {
        if (photo == null) return "PENDENTE";
        if (hasLocalFile(photo.localPath)) return "LOCAL";
        if (photo.ftpPath != null && !photo.ftpPath.trim().isEmpty()) return "FTP";
        return "PENDENTE";
    }

    private FTPClient connect() throws Exception {
        BackupManager bm = new BackupManager(context);
        String host = bm.getFtpHost();
        String user = bm.getFtpUser();
        String pass = bm.getFtpPassword();
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalStateException("FTP nao configurado.");
        }

        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(FTP_CONNECT_TIMEOUT_MS);
        ftp.setDataTimeout(FTP_DATA_TIMEOUT_MS);
        ftp.setBufferSize(1024 * 128);
        ftp.connect(host, 21);
        if (!ftp.login(user, pass)) {
            disconnectQuietly(ftp);
            throw new IllegalStateException("Login FTP recusado.");
        }
        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }

    private void ensureRemoteDirectory(FTPClient ftp, String remoteDir) throws Exception {
        String originalDir = ftp.printWorkingDirectory();
        String[] parts = remoteDir.split("/");
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) continue;
            if (!ftp.changeWorkingDirectory(part)) {
                ftp.makeDirectory(part);
                if (!ftp.changeWorkingDirectory(part)) {
                    throw new IllegalStateException("Nao foi possivel criar pasta FTP: " + part);
                }
            }
        }
        if (originalDir != null && !originalDir.trim().isEmpty()) {
            ftp.changeWorkingDirectory(originalDir);
        }
    }

    private String getRemoteDirectory(int osId) {
        return REMOTE_BASE_DIR + "/os_" + osId;
    }

    private String buildRemotePath(int osId, String fileName) {
        return getRemoteDirectory(osId) + "/" + sanitizeFileName(fileName);
    }

    private File buildLocalCacheFile(OSPhoto photo) {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (base == null) base = context.getFilesDir();
        File cacheDir = new File(base, "PDV_Pro/OS_Fotos_FTP");
        String fileName = remoteFileName(photo.ftpPath);
        if (fileName.isEmpty()) {
            fileName = "os_" + photo.osId + "_foto_" + photo.id + ".jpg";
        }
        return new File(cacheDir, "os_" + photo.osId + "_" + sanitizeFileName(fileName));
    }

    private String remoteFileName(String remotePath) {
        if (remotePath == null) return "";
        int idx = remotePath.lastIndexOf('/');
        return idx >= 0 ? remotePath.substring(idx + 1) : remotePath;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "foto_" + System.currentTimeMillis() + ".jpg";
        }
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private long getRemoteSize(FTPClient ftp, String remotePath) {
        try {
            FTPFile file = ftp.mlistFile(remotePath);
            if (file != null) return file.getSize();
        } catch (Exception ignored) {
        }
        try {
            FTPFile[] files = ftp.listFiles(remotePath);
            if (files != null && files.length > 0) return files[0].getSize();
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void disconnectQuietly(FTPClient ftp) {
        if (ftp == null) return;
        try { if (ftp.isConnected()) ftp.logout(); } catch (Exception ignored) {}
        try { if (ftp.isConnected()) ftp.disconnect(); } catch (Exception ignored) {}
    }

    public static class OSPhoto {
        public int id;
        public int osId;
        public String localPath;
        public String ftpPath;
        public String status;
        public String userName;

        public OSPhoto(int id, int osId, String localPath, String ftpPath, String status, String userName) {
            this.id = id;
            this.osId = osId;
            this.localPath = localPath;
            this.ftpPath = ftpPath;
            this.status = status;
            this.userName = userName;
        }
    }

    public static class UploadResult {
        public final String remotePath;
        public final long bytes;

        UploadResult(String remotePath, long bytes) {
            this.remotePath = remotePath;
            this.bytes = bytes;
        }

        public String formatBytes() {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
