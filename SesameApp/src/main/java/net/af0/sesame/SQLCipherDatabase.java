package net.af0.sesame;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabaseHook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Singleton class wrapping the database interactions.
 */
public final class SQLCipherDatabase {
    static final String COLUMN_USERNAME = "username";
    static final String COLUMN_DOMAIN = "domain";
    private static final String TABLE_KEYS = "keys";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_PASSWORD = "password";
    private static final String COLUMN_REMARKS = "remarks";
    static final String DATABASE_CREATE = "create table " +
            TABLE_KEYS + "(" +
            COLUMN_ID + " integer primary key autoincrement, " +
            COLUMN_USERNAME + " text, " +
            COLUMN_DOMAIN + " text," +
            COLUMN_PASSWORD + " text," +
            COLUMN_REMARKS + " text);";
    private static final String[] allColumns_ = {
            // Stupid. I named the field "id" and SimpleCursorFactory expects "id". Rather than
            // rename and break compatibility to gracefully handle database upgrades, let's just
            // alias it. I hope nobody is reading this...
            COLUMN_ID + " AS _id", COLUMN_USERNAME, COLUMN_DOMAIN,
            COLUMN_PASSWORD, COLUMN_REMARKS};
    // In this version, fields are strings, not blobs.
    private static final int STRING_DATABASE_VERSION = 3;
    private static final int DATABASE_VERSION = 4;
    private static final String DATABASE_NAME = "keys.db";
    private static net.sqlcipher.database.SQLiteOpenHelper helper_;
    private static SQLiteDatabase database_;

    private static Record createRecord(SQLiteDatabase database,
                                       final char[] username, final char[] domain,
                                       final char[] password, final char[] remarks) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_USERNAME, Common.encode(username));
        values.put(COLUMN_DOMAIN, Common.encode(domain));
        values.put(COLUMN_PASSWORD, Common.encode(password));
        values.put(COLUMN_REMARKS, Common.encode(remarks));
        long id = database.insert(TABLE_KEYS, null, values);
        Cursor crs = database.query(TABLE_KEYS, allColumns_,
                COLUMN_ID + "=" + id, null, null, null, null);
        crs.moveToFirst();
        Record r = toRecord(crs);
        crs.close();
        return r;
    }

    public static void createRecord(final char[] username, final char[] domain,
                                    final char[] password, final char[] remarks,
                                    final Callbacks2<Boolean, Record> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Record r;
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    r = createRecord(database_, username, domain, password, remarks);
                    return r != null;
                } catch (Exception e) {
                    exception = e;
                    return false;
                }
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(r != null, r);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void deleteRecord(final long record_id,
                                    final Callbacks2<Boolean, Record> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    database_.delete(TABLE_KEYS, COLUMN_ID + "=" + record_id, null);
                } catch (Exception e) {
                    exception = e;
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean result) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(result, null);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void updateRecord(final Record r, final Callbacks2<Boolean, Record> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            protected Boolean doInBackground(Void... params) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_USERNAME, Common.encode(r.getUsername()));
                values.put(COLUMN_DOMAIN, Common.encode(r.getDomain()));
                values.put(COLUMN_PASSWORD, Common.encode(r.getPassword()));
                values.put(COLUMN_REMARKS, Common.encode(r.getRemarks()));
                try {
                    database_.update(TABLE_KEYS, values, COLUMN_ID + "=" + r.getId(), null);
                } catch (Exception e) {
                    exception = e;
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean result) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(result, r);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void getRecord(final long record_id, final Callbacks<Record> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Record r;
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    Cursor crs = database_.query(TABLE_KEYS, allColumns_,
                            COLUMN_ID + "=" + record_id,
                            null, null, null, null);
                    if (crs.getCount() == 0) {
                        crs.close();
                        return false;
                    }
                    crs.moveToFirst();
                    r = toRecord(crs);
                    crs.close();
                } catch (Exception e) {
                    exception = e;
                }
                return r != null;
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                callbacks.OnFinish(r);
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnCancelled();
                    }
                }
            }
        }.execute();
    }

    public static Cursor getAllCursor() {
        return database_.query(TABLE_KEYS, allColumns_, null, null, null, null,
                "LOWER(" + COLUMN_DOMAIN + "), LOWER(" + COLUMN_USERNAME + ")");
    }

    public static Cursor getContaining(String substr) {
        // TODO: Ensure this works on non-ASCII searches. It seems to in some cases (real phone) and
        // not others (emulator), so there may be an issue with mismatching locales?
        String s = DatabaseUtils.sqlEscapeString("%" + substr + "%");
        return database_.query(TABLE_KEYS, allColumns_,
                String.format("%s LIKE %s OR %s LIKE %s", COLUMN_DOMAIN, s, COLUMN_USERNAME, s),
                null, null, null,
                "LOWER(" + COLUMN_DOMAIN + "), LOWER(" + COLUMN_USERNAME + ")");
    }


    public static Record toRecord(Cursor crs) {
        Record r = new Record();
        r.setId(crs.getLong(0));
        r.setUsername(Common.decode(crs.getBlob(1)));
        r.setDomain(Common.decode(crs.getBlob(2)));
        r.setPassword(Common.decode(crs.getBlob(3)));
        r.setRemarks(Common.decode(crs.getBlob(4)));

        return r;
    }

    private static DatabaseMetadata.Database getMetadataFromPrefs(Context ctx) {
        DatabaseMetadata.Database metadata = DatabaseMetadata.Database.newBuilder()
                .setVersion(DATABASE_VERSION)
                .setKdfIter(Constants.KDF_ITER)
                .build();
        SharedPreferences prefs = ctx.getSharedPreferences(Constants.DB_METADATA_PREF,
                Context.MODE_PRIVATE);
        try {
            metadata =
                    DatabaseMetadata.Database.newBuilder(metadata).mergeFrom(
                            DatabaseMetadata.Database.parseFrom(Base64.decode(prefs.getString(
                                            Constants.DB_METADATA_PREF, ""),
                                    Base64.DEFAULT
                            ))
                    ).build();
        } catch (InvalidProtocolBufferException e) {
            Log.e("IMPORT", Log.getStackTraceString(e));
            // Go with defaults anyway. Uh oh...
        }
        return metadata;
    }

    public static synchronized void OpenDatabase(final Context ctx, final char[] password,
                                                 final Callbacks<Boolean> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    if (helper_ == null) {
                        SQLiteDatabase.loadLibs(ctx);
                        helper_ = new OpenHelper(ctx, getMetadataFromPrefs(ctx));
                    }
                    database_ = helper_.getWritableDatabase(password);
                } catch (Exception e) {
                    exception = e;
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(success);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    // The import/export format for the database is
    //   # bytes: DatabaseMetadata, serialized with writeDelimitedTo() (i.e. preceded by a size
    //            varint). Parse with parseDelimitedFrom().
    //      rest: SQLCipher DB
    // So we have to strip the first n bytes before passing the file off to SQLCipher.
    public static void ImportDatabase(final Context ctx, final InputStream input,
                                      final char[] password, final Callbacks<Boolean> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    if (isLocked()) {
                        throw new SQLiteException("Database must be unlocked");
                    }
                    // Get temporary path to write database minus metadata to.
                    File tmpDb = File.createTempFile(Constants.KEY_IMPORT_TMPNAME,
                            Constants.KEY_IMPORT_SUFFIX, ctx.getCacheDir());
                    DatabaseMetadata.Database metadata = DatabaseMetadata.Database.newBuilder()
                            .setVersion(DATABASE_VERSION)
                            .setKdfIter(Constants.KDF_ITER)
                            .build();
                    OutputStream tmpDbStr = new FileOutputStream(tmpDb);
                    try {
                        // Parse the DatabaseMetadata.
                        metadata = DatabaseMetadata.Database.parseDelimitedFrom(input);
                        // Now get the remaining buffer.
                        byte[] buf = new byte[1024];
                        int b;
                        while ((b = input.read(buf)) != -1) {
                            tmpDbStr.write(buf, 0, b);
                        }
                    } finally {
                        tmpDbStr.close();
                    }
                    // Now everything is hunky dory.
                    SQLiteDatabase imported = SQLiteDatabase.openDatabase(tmpDb.getPath(), password,
                            null, SQLiteDatabase.OPEN_READONLY, new DatabaseHook(metadata));
                    if (imported.getVersion() != DATABASE_VERSION &&
                            imported.getVersion() != STRING_DATABASE_VERSION) {
                        // Because we're not using OpenHelper here, we have to handle version
                        // mismatches ourselves.
                        throw new UnsupportedOperationException(
                                String.format("Upgrade not supported! (%d)", imported.getVersion()));
                    }
                    Cursor crs = imported.query(TABLE_KEYS, allColumns_, null, null, null, null,
                            null);
                    for (crs.moveToFirst(); !crs.isAfterLast(); crs.moveToNext()) {
                        if (imported.getVersion() == DATABASE_VERSION) {
                            Record r = toRecord(crs);
                            createRecord(database_, r.getUsername(), r.getDomain(), r.getPassword(),
                                    r.getRemarks());
                        } else {
                            // Get strings rather than blobs.
                            createRecord(database_, crs.getString(1).toCharArray(),
                                    crs.getString(2).toCharArray(),
                                    crs.getString(3).toCharArray(),
                                    crs.getString(4).toCharArray());
                        }
                    }
                    crs.close();
                    return true;
                } catch (Exception e) {
                    exception = e;
                    return false;
                }
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(success);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void ExportDatabase(final Context ctx, final OutputStream output,
                                      final Callbacks<Boolean> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    getMetadataFromPrefs(ctx).writeDelimitedTo(output);
                    InputStream rawInput = new FileInputStream(getDatabaseFilePath(ctx));
                    byte[] buf = new byte[1024];
                    int b;
                    BeginTransaction();
                    while ((b = rawInput.read(buf)) != -1) {
                        output.write(buf, 0, b);
                    }
                    rawInput.close();
                } catch (Exception e) {
                    exception = e;
                } finally {
                    EndTransaction();
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(success);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void CreateDatabase(Context ctx, char[] password, Callbacks<Boolean> callbacks) {
        if (Exists(ctx)) {
            callbacks.OnException(new SQLiteException("file already exists"));
        }
        // Store a DatabaseMetadata object with our creation defaults.
        DatabaseMetadata.Database metadata = DatabaseMetadata.Database.newBuilder().setVersion(
                DATABASE_VERSION).setKdfIter(Constants.KDF_ITER).build();
        SharedPreferences.Editor preferencesEditor = ctx.getSharedPreferences(
                Constants.DB_METADATA_PREF, Context.MODE_PRIVATE).edit();
        preferencesEditor.putString(Constants.DB_METADATA_PREF,
                Base64.encodeToString(metadata.toByteArray(), Base64.DEFAULT));
        preferencesEditor.commit();
        // Open normally.
        OpenDatabase(ctx, password, callbacks);
    }

    public static synchronized void DeleteDatabase(final Context ctx,
                                                   final Callbacks<Boolean> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    Lock();  // Throw away the open database handle.
                    ctx.deleteDatabase(DATABASE_NAME);
                } catch (Exception e) {
                    exception = e;
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(success);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static void ChangePassword(final String password, final Callbacks<Boolean> callbacks) {
        new AsyncTask<Void, Void, Boolean>() {
            Exception exception;

            @Override
            public Boolean doInBackground(Void... param) {
                try {
                    // TODO: Switch this from a String to a char[] and just manually escape.
                    database_.rawExecSQL("PRAGMA rekey = " + DatabaseUtils.sqlEscapeString(password)
                            + ";");
                } catch (Exception e) {
                    exception = e;
                    return false;
                }
                return true;
            }

            @Override
            protected void onPostExecute(final Boolean success) {
                if (callbacks != null) {
                    if (exception != null) {
                        callbacks.OnException(exception);
                    } else {
                        callbacks.OnFinish(success);
                    }
                }
            }

            @Override
            protected void onCancelled() {
                if (callbacks != null) {
                    callbacks.OnCancelled();
                }
            }
        }.execute();
    }

    public static synchronized boolean isLocked() {
        return database_ == null || !database_.isOpen();
    }

    public static void Lock() {
        if (helper_ != null) {
            helper_.close();
        }
        if (database_ != null) {
            database_.close();
        }
    }

    public static boolean Exists(Context ctx) {
        return ctx.getDatabasePath(DATABASE_NAME).exists();
    }

    public static File getDatabaseFilePath(Context ctx) {
        return ctx.getDatabasePath(DATABASE_NAME);
    }

    // Begin a transaction on the open database. Useful for preventing writes during, say, a file
    // backup.
    public static synchronized void BeginTransaction() {
        if (!isLocked()) {
            database_.beginTransaction();
        }
    }

    public static synchronized void EndTransaction() {
        if (!isLocked()) {
            database_.endTransaction();
        }
    }


    static interface Callbacks<T> {
        void OnFinish(T x);

        void OnException(Exception exception);

        void OnCancelled();
    }

    static interface Callbacks2<T1, T2> {
        void OnFinish(T1 x, T2 y);

        void OnException(Exception exception);

        void OnCancelled();
    }

    private static class OpenHelper extends net.sqlcipher.database.SQLiteOpenHelper {
        public OpenHelper(Context ctx, DatabaseMetadata.Database metadata) {
            super(ctx, DATABASE_NAME, null, DATABASE_VERSION, new DatabaseHook(metadata));
        }

        public void onCreate(SQLiteDatabase database) {
            database.execSQL(DATABASE_CREATE);
        }

        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion != STRING_DATABASE_VERSION || newVersion != DATABASE_VERSION) {
                throw new UnsupportedOperationException(
                        String.format("Upgrade not supported! (%d -> %d", oldVersion, newVersion));
            }
            // Rename table.
            database.execSQL("ALTER TABLE " + TABLE_KEYS + " RENAME TO tmp;");
            database.execSQL(DATABASE_CREATE);
            // Go through all the records in the old table.
            Cursor crs = database.query("tmp", allColumns_, null, null, null, null,
                    null);
            for (crs.moveToFirst(); !crs.isAfterLast(); crs.moveToNext()) {
                // Get strings rather than blobs, and create a new record.
                createRecord(database, crs.getString(1).toCharArray(),
                        crs.getString(2).toCharArray(),
                        crs.getString(3).toCharArray(),
                        crs.getString(4).toCharArray());
            }
            crs.close();
            // Drop the renamed table.
            database.execSQL("DROP TABLE tmp;");
        }
    }

    private static class DatabaseHook implements SQLiteDatabaseHook {
        final DatabaseMetadata.Database metadata_;

        public DatabaseHook(DatabaseMetadata.Database metadata) {
            metadata_ = metadata;
        }

        @Override
        public void preKey(SQLiteDatabase database) {
        }

        @Override
        public void postKey(SQLiteDatabase database) {
            database.rawExecSQL(String.format("PRAGMA kdf_iter = %d",
                    metadata_.getKdfIter()));
            database.rawExecSQL(String.format("PRAGMA cipher = '%s'",
                    metadata_.getCipher()));
        }
    }

    // Database model.
    public static class Record {
        private long id_;
        private char[] username_;
        private char[] domain_;
        private char[] password_;
        private char[] remarks_;

        public void forget() {
            Common.ZeroChars(username_);
            Common.ZeroChars(password_);
            Common.ZeroChars(domain_);
            Common.ZeroChars(remarks_);
        }

        public long getId() {
            return id_;
        }

        void setId(long id) {
            id_ = id;
        }

        public char[] getUsername() {
            return username_;
        }

        public void setUsername(char[] username) {
            username_ = username;
        }

        public char[] getDomain() {
            return domain_;
        }

        public void setDomain(char[] domain) {
            domain_ = domain;
        }

        public char[] getPassword() {
            return password_;
        }

        public void setPassword(char[] password) {
            password_ = password;
        }

        public char[] getRemarks() {
            return remarks_;
        }

        public void setRemarks(char[] remarks) {
            remarks_ = remarks;
        }
    }
}