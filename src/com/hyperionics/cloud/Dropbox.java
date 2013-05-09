package com.hyperionics.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.RESTUtility;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.android.AuthActivity;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.jsonextract.JsonExtractionException;
import com.dropbox.client2.jsonextract.JsonList;
import com.dropbox.client2.jsonextract.JsonMap;
import com.dropbox.client2.jsonextract.JsonThing;
import com.dropbox.client2.session.AccessTokenPair;
import com.dropbox.client2.session.AppKeyPair;
import com.dropbox.client2.session.Session;
import com.hyperionics.avar.Lt;
import com.hyperionics.avar.R;
import com.hyperionics.avar.TtsApp;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hyperionics.util.FileUtil.*;
import static com.hyperionics.util.FileUtil.copyFile;
import static com.hyperionics.util.FileUtil.listFilesRecursive;

/**
 * Created with IntelliJ IDEA.
 * User: greg
 * Date: 1/19/13
 * Time: 3:17 PM
 * To change this template use File | Settings | File Templates.
 */
public class Dropbox {
    protected Dropbox() {
        // Exists only to defeat instantiation.
    }

    public static void init(String appKey, String appSecret, String topFolder, SharedPreferences prefs) {
        mTopFolder = topFolder;
        mPrefs = prefs;
        if (mDBApi == null) {
            AppKeyPair appKeys = new AppKeyPair(appKey, appSecret);
            AndroidAuthSession session = new AndroidAuthSession(appKeys, ACCESS_TYPE);
            mDBApi = new DropboxAPI<AndroidAuthSession>(session);
        }
        if (mTokens == null) {
            String key = mPrefs.getString("dropKey", null);
            String sec = mPrefs.getString("dropSec", null);
            mLastTopSyncTime = mPrefs.getLong("lastDropboxSync", 0);
            if (key != null && sec != null) {
                mTokens = new AccessTokenPair(key, sec);
                mDBApi.getSession().setAccessTokenPair(mTokens);
            }
        }
    }

    public static void setTopFolder(String topFolder, boolean reset) {
        if (reset && !mTopFolder.equals(topFolder)) {
            mPrefs.edit().remove("lastDropboxSync").commit();
            mLastTopSyncTime = 0;
            new File(topFolder + "/.config/.syncLocal").delete();
            new File(topFolder + "/.config/.syncRemote").delete();
        }
        mTopFolder = topFolder;
    }

    public static boolean isAuthorized() {
        return mDBApi != null && mTokens != null && mDBApi.getSession().isLinked();
    }

    public static void logout() {
        if (mDBApi != null) {
            mDBApi.getSession().unlink();
            mTokens = null;
            mState = null;
            SharedPreferences.Editor edt = mPrefs.edit();
            edt.remove("dropKey");
            edt.remove("dropSec");
            edt.remove("DropboxCache");
            edt.putLong("lastDropboxSync", mLastTopSyncTime);
            edt.commit();
        }
    }

    public static void auth(Activity activity) {
        //mDBApi.getSession().startAuthentication(activity);
        AppKeyPair appKeyPair = mDBApi.getSession().getAppKeyPair();

        // Check if the app has set up its manifest properly.
        Intent testIntent = new Intent(Intent.ACTION_VIEW);
        String scheme = "db-" + appKeyPair.key;
        String uri = scheme + "://" + AuthActivity.AUTH_VERSION + "/test";
        testIntent.setData(Uri.parse(uri));
        PackageManager pm = activity.getPackageManager();
        List<ResolveInfo> activities = pm.queryIntentActivities(testIntent, 0);

        if (0 == activities.size()) {
            throw new IllegalStateException("URI scheme in your app's " +
                    "manifest is not set up correctly. You should have a " +
                    "com.dropbox.client2.android.AuthActivity with the " +
                    "scheme: " + scheme);
        } else if (activities.size() > 1) {
            // Check to make sure there's no other app with this scheme.
            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle("Security alert");
            builder.setMessage("Another app on your phone may be trying to " +
                    "pose as the app you are currently using. The malicious " +
                    "app cannot access your account, but linking to Dropbox " +
                    "has been disabled as a precaution. Please contact " +
                    "support@dropbox.com.");
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            builder.show();
            return;
        } else {
            // Just one activity registered for the URI scheme. Now make sure
            // it's within the same package so when we return from web auth
            // we're going back to this app and not some other app.
            String authPackage = activities.get(0).activityInfo.packageName;
            if (!activity.getPackageName().equals(authPackage)) {
                throw new IllegalStateException("There must be an " +
                        "AuthActivity within your app's package registered " +
                        "for your URI scheme (" + scheme + "). However, it " +
                        "appears that an activity in a different package is " +
                        "registered for that scheme instead. If you have " +
                        "multiple apps that all want to use the same access" +
                        "token pair, designate one of them to do " +
                        "authentication and have the other apps launch it " +
                        "and then retrieve the token pair from it.");
            }
        }

        // Start Dropbox auth activity.
        Intent intent = new Intent(activity, DropboxAuthActivity.class);
        intent.putExtra(DropboxAuthActivity.EXTRA_INTERNAL_CONSUMER_KEY,
                appKeyPair.key);
        intent.putExtra(DropboxAuthActivity.EXTRA_INTERNAL_CONSUMER_SECRET,
                appKeyPair.secret);
        if (!(activity instanceof Activity)) {
            // If starting the intent outside of an Activity, must include
            // this. See startActivity(). Otherwise, we prefer to stay in
            // the same task.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (!DropboxAuthActivity.hasCallback()) {
            DropboxAuthActivity.setCallback(new DropboxAuthActivity.AuthFinished() {
                @Override
                public void authFinished() {
                    onAuthFinished();
                    if (mOpFin != null)
                        mOpFin.syncProgress(-1, null);
                }
            });
        }
        activity.startActivity(intent);
        // Above goes to AuthActivity.java, and if dropbox app not present, authenticates with
        // a web browser. When permission granted, comes back to AuthActivity.java - onNewIntent(),
        // puts the result and finishes. But - does not close web browser window.
        // Modify AuthActivity to use WebView instead?
    }

    public static void syncFolder() {
        if (mPreventRecursion)
            return;
        if (isAuthorized())
            new FolderSync().execute("");
        else if (mOpFin != null) {
            mOpFin.syncProgress(-1, null); // just signal failure
        }
    }

    public static void syncFolder(Activity activity) {
        if (mPreventRecursion)
            return;
        if (isAuthorized()) {
            syncFolder();
        } else {
            DropboxAuthActivity.setCallback(new DropboxAuthActivity.AuthFinished() {
                @Override
                public void authFinished() {
                    onAuthFinished();
                    if (isAuthorized())
                        syncFolder();
                    else if (mOpFin != null)
                        mOpFin.syncProgress(-2, null);
                }
            });
            auth(activity);
        }
    }

    public interface SyncProgress {
        public void syncProgress(int result, String msg); // result is: -2 not started, -1 in progress, 0 failed, 1 succeeded
    }

    public static void setCallback(SyncProgress of) {
        mOpFin = of;
    }

    public static boolean hasCallback() {
        return mOpFin != null;
    }

    public static String getLastError() {
        return mLastError;
    }

    public static long getLastSyncTime() {
        return mLastTopSyncTime;
    }

    // The rest below is private

    final static private Session.AccessType ACCESS_TYPE = Session.AccessType.APP_FOLDER;
    private static SharedPreferences mPrefs = null;
    private static DropboxAPI<AndroidAuthSession> mDBApi = null;
    private static AccessTokenPair mTokens = null;
    private static String mTopFolder = null;
    private static long mLastTopSyncTime = 0;
    private static SyncProgress mOpFin;
    private static boolean mPreventRecursion = false;
    private static HashMap<String,ModTimes> mLocalMtime = null; // used only if local file system doesn't allow mod time set
    private static String mLastError = null;

    private static void onAuthFinished() {
        DropboxAuthActivity.setCallback(null);
        if (mDBApi != null) {
            AndroidAuthSession ds = mDBApi.getSession();
            if (ds.getAccessTokenPair() == null && DropboxAuthActivity.authenticationSuccessful()) {
                try {
                    // MANDATORY call to complete auth.
                    // Sets the access token on the session
                    DropboxAuthActivity.finishAuthentication(ds);
                    mTokens = ds.getAccessTokenPair();
                    // Provide your own storeKeys to persist the access token pair
                    // A typical way to store tokens is using SharedPreferences
                    SharedPreferences.Editor edt = mPrefs.edit();
                    edt.putString("dropKey", mTokens.key);
                    edt.putString("dropSec", mTokens.secret);
                    edt.commit();
                } catch (IllegalStateException e) {
                    Lt.df("Error authenticating Dropbox account" + e);
                }
            }
        }
    }

    private static class FolderSync extends AsyncTask<String, String, Boolean> {
        private static FolderSync thisFs = null;

        protected Boolean doInBackground(String... folders) {
            return internalSyncFolder(folders[0]);
        }

        protected void onPreExecute() {
            mPreventRecursion = true;
            thisFs = this;
            mLastError = null;
        }

        protected void onProgressUpdate(String... msg) {
            if (mOpFin != null)
                mOpFin.syncProgress(-1, msg[0]);
        }

        protected void onPostExecute(Boolean result) {
            thisFs = null;
            mPreventRecursion = false;
            if (mOpFin != null)
                mOpFin.syncProgress(result ? 1 : 0, null);
        }

        static void sendMsg(String msg) {
            thisFs.publishProgress(msg);
        }
    }

    private static boolean internalSyncFolder(String folderToSync) {
        Lt.df("---------- Enter internalSyncFolder()");
        try {
            doUpdate();
        } catch (DropboxException e) {
            mLastError = "Can't sync folder: " + e;
            Lt.df("---------- Exit 1 internalSyncFolder()");
            return false;
        }
        if (folderToSync == null || folderToSync.isEmpty())
            folderToSync = mTopFolder;
        File dir = new File(folderToSync);
        if (!dir.isDirectory()) {
            mLastError = "Folder sync for non-folder: " + folderToSync;
            Lt.df("---------- Exit 2 internalSyncFolder()");
            return false;
        }
        loadLocalMtimes();
        if (mState == null) {
            Lt.df("mState is null in internalSyncFolder(), recursion error?");
            return false;
        }
        Content.Folder tree = mState.tree;
        if (!folderToSync.equals(mTopFolder)) {
            tree = findSubFolder(tree, folderToSync);
            if (tree == null) { // request to sync local folder that was not found in Dropbox, upload the folder.
                Lt.df("---------- Exit 3 internalSyncFolder()");
                return uploadToDropbox(dir, null);
            }
        }

        boolean ret = syncMissingLocalFiles(tree);
        if (ret)
            ret = syncRest(folderToSync, tree);

        if (ret && folderToSync.equals(mTopFolder)) {
            mLastTopSyncTime = System.currentTimeMillis();
            SharedPreferences.Editor edt = mPrefs.edit();
            edt.putLong("lastDropboxSync", mLastTopSyncTime);
            edt.commit();
        }
        saveLocalMtimes();
        if (ret)
            mLastError = TtsApp.getContext().getString(R.string.sync_ok);
        Lt.df("---------- Exit 4 internalSyncFolder()");
        return ret;
    }

    private static boolean syncMissingLocalFiles(Content.Folder tree) {
        boolean ret = true;
        // This loop goes through Dropbox tree and downloads new files/folders.
        // If we find a file that exist in Dropbox, but not locally, we need to
        // figure out if it's a new file to be downloaded, or an old file that
        // has been deleted locally and needs to be deleted in Dropbox too.
        // If remote file mod time is newer than mLastTopSyncTime, we download it.
        // Otherwise we delete this file from Dropbox.

        // Missing local files & folders only: DOWNLOAD from Dropbox or DELETE in Dropbox
        for (Map.Entry<String,Node> chld : tree.children.entrySet()) {
            Node n = chld.getValue();
            if (!n.path.startsWith("/.config/.")) { // ignore hidden config files, even if existing remotely
                File file = new File(mTopFolder + n.path);
                if (!file.exists()) { // should we delete this file in Dropbox?
                    long remoteModTime = n.content.lastModified;
                    if (remoteModTime >= mLastTopSyncTime) {
                        if (!downloadFromDropbox(n))
                            ret = false;
                    } else {
                        FolderSync.sendMsg("Delete remote: " + n.path);
                        try {
                            mDBApi.delete(n.path);
                        } catch (DropboxException e) {
                            mLastError = "Error deleting remote: " + n.path + "   " + e;
                            ret = false;
                        }
                    }
                } else if (n.content instanceof Content.Folder) { // existing directory, sync missing files in it.
                    ret = syncMissingLocalFiles((Content.Folder) n.content);
                }
                if (!ret)
                    break;
            }
        }
        return ret;
    }

    private static boolean syncRest(String folderToSync, Content.Folder tree) {
        File dir = new File(folderToSync);
        if (!dir.isDirectory()) // sanity check
            return false;
        if (tree == null)
            tree = mState.tree;
        boolean ret = true;
        // Missing Dropbox files: upload from local or delete from local
        // Mismatched mod time: upload or download as needed.
        for (File child : dir.listFiles()) {
            String fileName = child.getAbsolutePath().substring(mTopFolder.length());
            if (fileName.startsWith("/.config/.")) // ignore hidden local files
                continue;
            // Do we have this file or folder in Dropbox?
            // Upon downloading a file from Dropbox, set modified file to be identical...
            // Also upon upload to Dropbox, I get DropboxAPI.Entry, where the dropbox mod time will be returned?
            // and could set the same mod file on the local file... Or remember somewhere the rev of this file?
            boolean foundInDropbox = false;
            for (Map.Entry<String,Node> chld : tree.children.entrySet()) {
                Node n = chld.getValue();
                if (fileName.equals(n.path)) { // Found same file or folder name in Dropbox
                    foundInDropbox = true;
                    if (n.content instanceof Content.File) {
                        Content.File cf = (Content.File) n.content;
                        long localModTime = getLocalMtime(child);
                        long remoteModTime = cf.lastModified;
                        long diff = (localModTime - remoteModTime)/100; // discard difference smaller than 100 ms
                        if (diff < 0) { // remote file is newer
                            if (!downloadFromDropbox(n))
                                ret = false;
                        } else if (diff > 0) {
                            if (!uploadToDropbox(child, cf.rev))
                                ret = false;
                        }
                    } else {
                        // if same folder found on both, we need to sync it.
                        ret = syncRest(child.getAbsolutePath(), (Content.Folder) n.content);
                    }
                    break;
                }
                if (!ret)
                    break;
            }

            if (ret && !foundInDropbox) {
                // Not found in Dropbox. Is it a new file or folder that should be uploaded,
                // or an old one that was deleted in dropbox and we should delete it too?
                boolean isNew = false;
                try {
                    DropboxAPI.Entry entry = mDBApi.metadata(fileName, 1, null, false, null);
                    // If the file was deleted from Dropbox, gets a valid entry with rev, modified time etc,
                    // and isDeleted is set to true. Modified time is set to deletion time, and
                    // clientMTime is 0.
                    if (entry.isDeleted && RESTUtility.parseDate(entry.modified).getTime() < getLocalMtime(child)) {
                        isNew = true;
                    }
                } catch (DropboxServerException e) {
                    // If file not found, gets here with
                    // "DropboxServerException (nginx): 404 Not Found (Path '/AbcDef.ghi' not found)
                    // This means that we have a new file and should upload it.
                    if (e.error == DropboxServerException._404_NOT_FOUND) {
                        isNew = true;
                    }
                } catch (DropboxException e) {
                    ret = false;
                    mLastError = "Error obtaining remote info for: " + fileName + "   " + e;
                }

                if (isNew) { // Upload this file or folder.
                    if (!uploadToDropbox(child, null)) // new file or folder, so rev is null
                        ret = false;
                } else { // Delete local copy of this file or folder
                    FolderSync.sendMsg("Delete local: " + fileName);
                    DeleteRecursive(child);
                }
            }
            if (!ret)
                break;
        }

        return ret;
    }

    private static void DeleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                DeleteRecursive(child);

        fileOrDirectory.delete();
    }

    private static Content.Folder findSubFolder(Content.Folder tree, String searchedPath) {
        for (Map.Entry<String,Node> child : tree.children.entrySet()) {
            Node n = child.getValue();
            String path = n.path;
            if (n.content instanceof Content.Folder && path != null) {
                if (path.equals(searchedPath)) {
                    return (Content.Folder) n.content;
                }
                else {
                    // Recurse on children.
                    Content.Folder f = (Content.Folder) n.content;
                    f = findSubFolder(f, searchedPath);
                    if (f != null)
                        return f;
                }
            }
        }
        return null; // not found
    }

    private static Content.File findFile(Content.Folder tree, String searchedPath) {
        for (Map.Entry<String,Node> child : tree.children.entrySet()) {
            Node n = child.getValue();
            String path = n.path;
            if (n.content instanceof Content.File) {
                if (path != null && path.equals(searchedPath))
                    return (Content.File) n.content;
            }
            else {
                // Recurse on children.
                Content.Folder fold = (Content.Folder) n.content;
                Content.File f = findFile(fold, searchedPath);
                if (f != null)
                    return f;
            }
        }
        return null; // not found
    }

    private static boolean uploadToDropbox(File file, String parentRev) {
        boolean ret = false;
        String fileName = file.getAbsolutePath().substring(mTopFolder.length());

        if (file.isDirectory()) {
            try {
                mDBApi.createFolder(fileName);
                ret = true; // otherwise will show fail if dir empty
                for (File child : file.listFiles()) {
                    // We request folder upload only for new folders, existing locally but not remotely,
                    // so it's OK to pass rev null for each file and sub-folder we upload from here.
                    ret = uploadToDropbox(child, null);
                    if (!ret)
                        break;
                }
            } catch (DropboxException e) {
                ret = false;
                mLastError = "Dropbox upload, exception: " + e;
            }

        } else {
            FileInputStream inputStream = null;
            DropboxAPI.Entry entry = null;
            try {
                inputStream = new FileInputStream(file);
                entry = mDBApi.putFile(fileName, inputStream, file.length(), parentRev, null);
            } catch (Exception e) {
                mLastError = "Dropbox upload, exception: " + e;
            } finally {
                if (inputStream != null) try {
                    inputStream.close();
                } catch (IOException e) {}
                // if we get entry != null, the upload was succesful
                if (entry != null) {
                    long modTime = RESTUtility.parseDate(entry.modified).getTime();
                    setLocalMtime(file, modTime);
                    // Also, if this was an update of older file in Dropbox, need to find this
                    // file in mState and set the same modTime, as Dropbox Delta API does not
                    // update mod times for existing files...
                    Content.File cf = findFile(mState.tree, fileName);
                    if (cf != null) {
                        cf.lastModified = modTime;
                    }
                    ret = true;
                    FolderSync.sendMsg("Uploaded: " + fileName);
                }
            }
        }
        return ret;
    }

    private static void setLocalMtime(File file, Long modTime) {
        // Note, some file systems are mounted with a permission to change modified time of a file.
        // This happens also on my Samsung Galaxy Note II. Then this method of syncing fails.
        boolean b = file.setLastModified(modTime);
        if (!b) {
            Lt.d("-- setLastModified() returned " + b);
            if (mLocalMtime == null)
                mLocalMtime = new HashMap<String, ModTimes>();
            mLocalMtime.put(file.getAbsolutePath().substring(mTopFolder.length()), new ModTimes(file.lastModified(), modTime));
        }
    }

    private static void setLocalMtime(File file, Long modTime, Long currModTime) {
        // Special version for moveContents()
        boolean b = file.setLastModified(modTime);
        if (!b) {
            Lt.d("-- setLastModified() returned " + b);
            if (mLocalMtime == null)
                mLocalMtime = new HashMap<String, ModTimes>();
            mLocalMtime.put(file.getAbsolutePath().substring(mTopFolder.length()), new ModTimes(currModTime, modTime));
        }
    }

    private static long getLocalMtime(File file) {
        if (mLocalMtime == null)
            return file.lastModified();
        ModTimes mt = null;
        try {
            mt = mLocalMtime.get(file.getAbsolutePath().substring(mTopFolder.length()));
        } catch (ClassCastException e) { /*if we changed code, we could have old version objects saved... */ }
        long ltFile = file.lastModified();
        if (mt == null || mt.local != ltFile)
            return ltFile;
        return mt.remote;
    }

    private static void saveLocalMtimes() {
        if (mLocalMtime != null) {
            int count = 0;
            JSONObject o = new JSONObject();
            for (Map.Entry<String,ModTimes> c : mLocalMtime.entrySet()) {
                // save only the entries for files that still exist
                if (new File(mTopFolder + c.getKey()).exists()) {
                    o.put(c.getKey(), c.getValue().toJson());
                    count++;
                }
            }

            try {
                File sf = new File(mTopFolder + "/.config/.syncLocal");
                if (count == 0) {
                    sf.delete();
                } else {
                    DataOutputStream dt = new DataOutputStream(new FileOutputStream(sf));
                    dt.writeUTF(o.toJSONString());
                    dt.flush();
                    dt.close();
                }
            } catch (IOException e) {}
            mLocalMtime = null;
        }
    }

    private static void loadLocalMtimes() {
        String js;

        try {
            DataInputStream dt = new DataInputStream(new FileInputStream(mTopFolder + "/.config/.syncLocal"));
            js = dt.readUTF();
            dt.close();
        } catch (IOException e) {
            js = mPrefs.getString("mLocalMtime", null); // get old version saved string
            if (js != null)
                mPrefs.edit().remove("mLocalMtime").commit(); // no longer needed
        }

        if (js != null) try {
            JSONObject ja = (JSONObject) new JSONParser().parse(js);
            mLocalMtime = new HashMap<String, ModTimes>();
            for (Object o : ja.entrySet()) {
                Map.Entry<String, JSONArray> e = (Map.Entry<String, JSONArray>) o;
                long local = (Long) e.getValue().get(0);
                long remote = (Long) e.getValue().get(1);
                mLocalMtime.put(e.getKey(), new ModTimes(local, remote));
            }
        } catch (Exception ex) {
            mLocalMtime = null;
            Lt.df("ERROR: Saved state string isn't valid JSON: " + ex.getMessage());
        }
    }

    public static boolean moveContents(File dstDir) {
        // move the entire top directory to another location, preserving sync data
        File srcDir = new File(mTopFolder);
        if (!srcDir.isDirectory() || !(srcDir.canRead() && srcDir.canWrite()))
            return false;
        if (!dstDir.exists())
            dstDir.mkdirs();
        if (!dstDir.isDirectory())
            return false;
        loadLocalMtimes();
        ArrayList<File> files = listFilesRecursive(srcDir);
        String srcPath = srcDir.getAbsolutePath();
        String dstPath = dstDir.getAbsolutePath();
        for (File f : files) {
            File parentDir = new File(f.getParent().replace(srcPath, dstPath));
            parentDir.mkdirs();
            if (!parentDir.exists())
                return false;
            String newName = parentDir.getAbsolutePath() + "/" + f.getName();
            if (!f.renameTo(new File(newName))) {
                File newFile = new File(newName);
                if (!copyFile(f, newFile))
                    return false;
                // For any sync operation's sake, if all went well, try to set the same mod time
                setLocalMtime(newFile, getLocalMtime(f), newFile.lastModified());
            }
            setLocalMtime(parentDir, getLocalMtime(f.getParentFile()), parentDir.lastModified());
        }
        setLocalMtime(dstDir, getLocalMtime(srcDir), dstDir.lastModified());
        mTopFolder = dstDir.getAbsolutePath();
        saveLocalMtimes(); // save it to the new folder!
        return true;
    }

    private static boolean downloadFromDropbox(Node n) {
        String fileName = n.path;
        long remoteModTime = n.content.lastModified;
        boolean ret = false;
        File file = new File(mTopFolder + fileName);

        if (n.content instanceof Content.Folder) { // Folder not found locally, create and recursively download.
            if (file.mkdirs()) {
                setLocalMtime(file, remoteModTime);
            }
            Content.Folder cf = (Content.Folder) n.content;
            for (Map.Entry<String,Node> chld : cf.children.entrySet()) {
                Node nn = chld.getValue();
                ret = downloadFromDropbox(nn);
                if (!ret)
                    break;
            }

        } else { // Download file
            FileOutputStream outputStream = null;
            DropboxAPI.DropboxFileInfo info = null;
            try {
                new File(file.getParent()).mkdirs();
                outputStream = new FileOutputStream(file);
                info = mDBApi.getFile(fileName, null, outputStream, null);
            } catch (DropboxException e) {
                mLastError = "Dropbox download exception: " + e;
            } catch (FileNotFoundException e) {
                mLastError = "Remote file not found: " + fileName;
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {}
                }
                if (info != null && file != null) { // download succeeded, set mod time as in Dropbox
                    setLocalMtime(file, remoteModTime);
                    FolderSync.sendMsg("Downloaded: " + fileName);
                    ret = true;
                }
            }
        }

        return ret;
    }

    private static State mState = null;

    private static void doUpdate() throws DropboxException {
        int pageLimit = -1;
        // Load state.
        if (mState == null) {
            mState = State.load();
        }

        int pageNum = 0;
        boolean changed = false;
        String cursor = mState.cursor;
        while (pageLimit < 0 || (pageNum < pageLimit)) {
            // Get /delta results from Dropbox
            DropboxAPI.DeltaPage<DropboxAPI.Entry> page = mDBApi.delta(cursor);
            pageNum++;
            if (page.reset) {
                mState.tree.children.clear();
                changed = true;
            }
            // Apply the entries one by one.
            for (DropboxAPI.DeltaEntry<DropboxAPI.Entry> e : page.entries) {
                applyDelta(mState.tree, e);
                changed = true;
            }
            cursor = page.cursor;
            if (!page.hasMore) break;
        }

        // Save state.
        if (changed) {
            mState.cursor = cursor;
            mState.save();
        }
    }

    private static void applyDelta(Content.Folder parent, DropboxAPI.DeltaEntry<DropboxAPI.Entry> e) {
        Path path = Path.parse(e.lcPath);
        DropboxAPI.Entry md = e.metadata;

        if (md != null) {
            //Lt.d("+ " + e.lcPath);
            // Traverse down the tree until we find the parent of the entry we
            // want to add.  Create any missing folders along the way.
            Long modTime = RESTUtility.parseDate(md.modified).getTime();
            for (String b : path.branch) {
                Node n = getOrCreateChild(parent, b);
                if (n.content instanceof Content.Folder) {
                    parent = (Content.Folder) n.content;
                } else {
                    // No folder here, automatically create an empty one.
                    n.content = parent = new Content.Folder(modTime);
                }
            }

            // Create the file/folder here.
            Node n = getOrCreateChild(parent, path.leaf);
            n.path = md.path;  // Save the un-lower-cased path.
            if (md.isDir) {
                // Only create an empty folder if there isn't one there already.
                if (!(n.content instanceof Content.Folder)) {
                    n.content = new Content.Folder(modTime);
                }
            }
            else {
                n.content = new Content.File(md.bytes, modTime, md.rev);
            }
        }
        else {
            //Lt.df("- " + e.lcPath);
            // Traverse down the tree until we find the parent of the entry we
            // want to delete.
            boolean missingParent = false;
            for (String b : path.branch) {
                Node n = parent.children.get(b);
                if (n != null && n.content instanceof Content.Folder) {
                    parent = (Content.Folder) n.content;
                } else {
                    // If one of the parent folders is missing, then we're done.
                    missingParent = true;
                    break;
                }
            }

            if (!missingParent) {
                parent.children.remove(path.leaf);
            }
        }
    }

    private static Node getOrCreateChild(Content.Folder folder, String lowercaseName) {
        Node n = folder.children.get(lowercaseName);
        if (n == null) {
            folder.children.put(lowercaseName, n = new Node(null, null));
        }
        return n;
    }

    /**
     * Represent a path as a list of ancestors and a leaf name.
     *
     * For example, "/a/b/c" -> Path(["a", "b"], "c")
     */
    private static final class Path {
        public final String[] branch;
        public final String leaf;

        public Path(String[] branch, String leaf)
        {
            assert branch != null;
            assert leaf != null;
            this.branch = branch;
            this.leaf = leaf;
        }

        public static Path parse(String s)
        {
            assert s.startsWith("/");
            String[] parts = s.split("/");
            assert parts.length > 0;

            String[] branch = new String[parts.length-2];
            System.arraycopy(parts, 1, branch, 0, branch.length);
            String leaf = parts[parts.length-1];
            return new Path(branch, leaf);
        }
    }

    private static final class State { // State model (load+save to JSON)
        public final Content.Folder tree;

        public State(Content.Folder tree) {
            this.tree = tree;
        }

        public String cursor;

        public void save() {
            JSONObject jstate = new JSONObject();
            // Convert tree
            JSONObject jtree = tree.toJson();
            jstate.put("tree", jtree);

            // Convert cursor, if present.
            if (cursor != null) {
                jstate.put("cursor", cursor);
            }

            try {
                File sf = new File(mTopFolder + "/.config/.syncRemote");
                DataOutputStream dt = new DataOutputStream(new FileOutputStream(sf));
                dt.writeUTF(jstate.toJSONString());
                dt.flush();
                dt.close();
            } catch (IOException e) {}
        }

        public static State load() {
            String js;

            try {
                DataInputStream dt = new DataInputStream(new FileInputStream(mTopFolder + "/.config/.syncRemote"));
                js = dt.readUTF();
                dt.close();
            } catch (IOException e) {
                js = mPrefs.getString("DropboxCache", null); // get old version saved string
                if (js != null)
                    mPrefs.edit().remove("DropboxCache").commit(); // no longer needed
            }


            if (js == null)
                return new State(new Content.Folder(0L));

            JsonThing j;
            try {
                j = new JsonThing(new JSONParser().parse(js));
            } catch (ParseException ex) {
                Lt.df("ERROR: Saved state string isn't valid JSON: " + ex.getMessage());
                return new State(new Content.Folder(0L));
            }

            JsonMap jm = null;
            JsonMap jtree = null;
            JsonThing jcursor = null;
            try {
                jm = j.expectMap();
                jtree = jm.get("tree").expectMap();
                Content.Folder tree = Content.Folder.fromJson(jtree);

                State state = new State(tree);

                jcursor = jm.getOrNull("cursor");
                if (jcursor != null) {
                    state.cursor = jcursor.expectString();
                }

                return state;
            }
            catch (JsonExtractionException ex) {
                Lt.df("ERROR: State file has incorrect structure: " + ex.getMessage());
            }
            Lt.d("Returning new State(new Content.Folder(0L));");
            return new State(new Content.Folder(0L));
        }
    }

    private static final class Node  {
        // We represent our local cache as a tree of 'Node' objects.
        /**
         * The original path of the file.  We track this separately because
         * Folder.children only contains lower-cased names.
         */
        public String path;

        /**
         * The node content (either Content.File or Content.Folder)
         */
        public Content content;

        public Node(String path, Content content)
        {
            this.path = path;
            this.content = content;
        }

        public final JSONArray toJson()
        {
            JSONArray array = new JSONArray();
            array.add(path);
            array.add(content.toJson());
            return array;
        }

        public static Node fromJson(JsonThing t)
                throws JsonExtractionException
        {
            JsonList l = t.expectList();
            String path = l.get(0).expectStringOrNull();
            JsonThing jcontent = l.get(1);
            Content content;
            if (jcontent.isList()) {
                content = Content.File.fromJson(jcontent.expectList());
            } else if (jcontent.isMap()) {
                content = Content.Folder.fromJson(jcontent.expectMap());
            } else {
                throw jcontent.unexpected();
            }
            return new Node(path, content);
        }
    }

    private static abstract class Content {
        public Long lastModified;
        public abstract Object toJson();

        public static final class Folder extends Content {
            public static final String MOD_KEY = "\\\\lastMod";
            public final HashMap<String,Node> children = new HashMap<String,Node>();

            public Folder(Long lastModified) {
                this.lastModified = lastModified;
            }

            public JSONObject toJson() {
                JSONObject o = new JSONObject();
                // Error here! Find another way to save and read back lastModified.
                StringWriter out = new StringWriter();
                o.put(MOD_KEY, lastModified);
                for (Map.Entry<String,Node> c : children.entrySet()) {
                    o.put(c.getKey(), c.getValue().toJson());
                }
                return o;
            }

            public static Folder fromJson(JsonMap j) throws JsonExtractionException {
                long date = j.get(MOD_KEY).expectInt64();
                Folder folder = new Folder(date);
                for (Map.Entry<String,JsonThing> e : j) {
                    if (!e.getKey().equals(MOD_KEY))
                        folder.children.put(e.getKey(), Node.fromJson(e.getValue()));
                }
                return folder;
            }
        }

        public static final class File extends Content {
            public final long size;
            public final String rev;

            public File(long size, Long lastModified, String rev)
            {
                this.size = size;
                this.lastModified = lastModified;
                this.rev = rev;
            }

            public JSONArray toJson()
            {
                JSONArray j = new JSONArray();
                j.add(size);
                j.add(lastModified);
                j.add(rev);
                return j;
            }

            public static File fromJson(JsonList l)
                    throws JsonExtractionException
            {
                return new File(l.get(0).expectInt64(), l.get(1).expectInt64(), l.get(2).expectString());
            }
        }
    }

    private static class ModTimes {
        Long local = Long.valueOf(0);
        Long remote = Long.valueOf(0);

        public ModTimes(long local, long remote) {
            this.local = local;
            this.remote = remote;
        }

        public JSONArray toJson()
        {
            JSONArray j = new JSONArray();
            j.add(local);
            j.add(remote);
            return j;
        }

        public static ModTimes fromJson(JsonList l)
                throws JsonExtractionException
        {
            return new ModTimes(l.get(0).expectInt64(), l.get(1).expectInt64());
        }

    }
}
