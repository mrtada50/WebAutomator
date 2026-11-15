package com.webautomator.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.webautomator.app.ui.CreateFragment
import com.webautomator.app.ui.HomeFragment
import com.webautomator.app.ui.ResultsFragment
import com.webautomator.app.ui.help
import com.webautomator.app.work.MonitorService

class MainActivity : AppCompatActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, R.string.notif_denied, Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fm = supportFragmentManager
        if (savedInstanceState == null) {
            fm.beginTransaction()
                .add(R.id.container, HomeFragment(), TAG_HOME)
                .add(R.id.container, CreateFragment(), TAG_CREATE)
                .add(R.id.container, ResultsFragment(), TAG_RESULTS)
                .commitNow()
            fm.beginTransaction()
                .hide(fm.findFragmentByTag(TAG_CREATE)!!)
                .hide(fm.findFragmentByTag(TAG_RESULTS)!!)
                .commitNow()
        }

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener { item ->
            val home = fm.findFragmentByTag(TAG_HOME)!!
            val create = fm.findFragmentByTag(TAG_CREATE)!!
            val results = fm.findFragmentByTag(TAG_RESULTS)!!
            val tx = fm.beginTransaction().hide(home).hide(create).hide(results)
            when (item.itemId) {
                R.id.nav_home -> tx.show(home)
                R.id.nav_create -> tx.show(create)
                R.id.nav_results -> tx.show(results)
                else -> return@setOnItemSelectedListener false
            }
            tx.commit()
            true
        }

        nav.findViewById<android.view.View>(R.id.nav_home)?.help(R.string.h_nav_home)
        nav.findViewById<android.view.View>(R.id.nav_create)?.help(R.string.h_nav_create)
        nav.findViewById<android.view.View>(R.id.nav_results)?.help(R.string.h_nav_results)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (intent.getBooleanExtra(EXTRA_RESUME, false)) MonitorService.refresh(this)
        if (savedInstanceState == null) handleEdit(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleEdit(intent)
    }

    /** Opens the Create tab in "edit script" mode so the user re-picks elements on the real page. */
    private fun handleEdit(i: Intent) {
        val id = i.getLongExtra(EXTRA_EDIT_ID, 0L)
        if (id == 0L) return
        val index = i.getIntExtra(EXTRA_EDIT_INDEX, -1)
        i.removeExtra(EXTRA_EDIT_ID)
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_create
        (supportFragmentManager.findFragmentByTag(TAG_CREATE) as? CreateFragment)?.startEdit(id, index)
    }

    companion object {
        const val EXTRA_RESUME = "resume_monitor"
        const val EXTRA_EDIT_ID = "edit_script_id"
        const val EXTRA_EDIT_INDEX = "edit_step_index"
        private const val TAG_HOME = "home"
        private const val TAG_CREATE = "create"
        private const val TAG_RESULTS = "results"
    }
}
