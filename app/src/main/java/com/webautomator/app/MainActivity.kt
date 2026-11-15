package com.webautomator.app

import android.Manifest
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
    }

    companion object {
        const val EXTRA_RESUME = "resume_monitor"
        private const val TAG_HOME = "home"
        private const val TAG_CREATE = "create"
        private const val TAG_RESULTS = "results"
    }
}
