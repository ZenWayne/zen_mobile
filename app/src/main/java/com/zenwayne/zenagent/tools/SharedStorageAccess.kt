package com.zenwayne.zenagent.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Remembers the directory tree the user authorized for `/shared` (spec §8-P3).
 *
 * The grant itself lives in the system: `takePersistableUriPermission` survives
 * reboots and app upgrades, so all this stores is which URI to ask about. Every
 * lookup re-checks the live grant, which means a permission revoked from
 * Android's settings closes the door here too, without the app noticing
 * anything else.
 */
class SharedStorageAccess(context: Context) {

    private val appContext = context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The authorized tree, or null when none is granted (or it was revoked). */
    fun authorizedUri(): Uri? {
        val stored = prefs.getString(KEY_TREE_URI, null) ?: return null
        val uri = Uri.parse(stored)
        val granted = appContext.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (granted) uri else null
    }

    /**
     * Persists the grant returned by `ACTION_OPEN_DOCUMENT_TREE`. Any previous
     * tree is released — one shared root at a time keeps `/shared` unambiguous.
     */
    fun authorize(uri: Uri) {
        release()
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    /** Drops the grant; `/shared` starts erroring again on the next tool call. */
    fun release() {
        val stored = prefs.getString(KEY_TREE_URI, null)
        if (stored != null) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    /**
     * The backend [FsRouter] should use for `/shared`, or null if unauthorized.
     *
     * The tree is re-resolved on every traversal rather than captured once, so
     * a grant revoked mid-run stops resolving on the next tool call.
     */
    fun backend(): FsBackend? {
        if (authorizedUri() == null) return null
        return SafSharedStorage {
            authorizedUri()
                ?.let { DocumentFile.fromTreeUri(appContext, it) }
                ?.let { DocumentFileNode(appContext.contentResolver, it) }
        }
    }

    /** Last path segment of the tree, for display in Settings. */
    fun displayName(): String? = authorizedUri()?.let { uri ->
        Uri.decode(uri.lastPathSegment)?.substringAfterLast(':')?.ifEmpty { null }
    }

    private companion object {
        const val PREFS = "zen_shared_storage"
        const val KEY_TREE_URI = "tree_uri"
    }
}
