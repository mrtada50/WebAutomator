package com.webautomator.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.webautomator.app.R
import com.webautomator.app.data.AppDatabase
import com.webautomator.app.data.RunEntity
import com.webautomator.app.data.ScriptEntity
import com.webautomator.app.util.Export
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ResultsFragment : Fragment() {

    private var scripts: List<ScriptEntity> = emptyList()
    private var selectedId = 0L // 0 = all scripts
    private var listJob: Job? = null
    private var pendingText = ""
    private lateinit var spinner: Spinner
    private lateinit var adapter: RowAdapter<RunEntity>
    private lateinit var empty: TextView

    private val csvLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> writeTo(uri) }
    private val jsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> writeTo(uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val c = requireContext()
        val root = c.vbox(12)
        root.add(c.tv(getString(R.string.nav_results), 20f, true))

        spinner = Spinner(c)
        root.add(spinner, top = 8)

        val row = c.hbox()
        row.add(c.btn(R.string.export_csv) { export(true) }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.add(c.btn(R.string.export_json) { export(false) }, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f, start = 8)
        root.add(row, top = 8)

        empty = c.tv(getString(R.string.no_results), 14f)
        root.add(empty, top = 8)

        adapter = RowAdapter { ctx, box, r, _ -> bindRun(ctx, box, r) }
        val list = RecyclerView(c)
        list.layoutManager = LinearLayoutManager(c)
        list.adapter = adapter
        list.addItemDecoration(DividerItemDecoration(c, DividerItemDecoration.VERTICAL))
        root.add(list, ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f, top = 8)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val newId = if (position == 0) 0L else scripts.getOrNull(position - 1)?.id ?: 0L
                if (newId != selectedId) {
                    selectedId = newId
                    startListing()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.scripts().observeAll().collect { list ->
                    scripts = list
                    val names = listOf(getString(R.string.all_scripts)) + list.map { it.name }
                    spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, names)
                    val idx = list.indexOfFirst { it.id == selectedId }
                    spinner.setSelection(if (idx >= 0) idx + 1 else 0)
                    if (idx < 0) selectedId = 0L
                    startListing()
                }
            }
        }
    }

    private fun startListing() {
        listJob?.cancel()
        val db = AppDatabase.get(requireContext())
        listJob = viewLifecycleOwner.lifecycleScope.launch {
            val flow = if (selectedId == 0L) db.runs().observeLatest(300) else db.runs().observeForScript(selectedId, 300)
            flow.collect {
                adapter.items = it
                empty.visibility = if (it.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun export(csv: Boolean) {
        val db = AppDatabase.get(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            val runs = if (selectedId == 0L) db.runs().all() else db.runs().allForScript(selectedId)
            if (runs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.export_empty, Toast.LENGTH_SHORT).show()
                return@launch
            }
            pendingText = if (csv) Export.csv(runs) else Export.json(runs)
            if (csv) csvLauncher.launch("results.csv") else jsonLauncher.launch("results.json")
        }
    }

    private fun writeTo(uri: Uri?) {
        if (uri == null) return
        val ctx = requireContext()
        try {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(pendingText.toByteArray(Charsets.UTF_8)) }
            Toast.makeText(ctx, R.string.exported, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, R.string.export_fail, Toast.LENGTH_LONG).show()
        }
    }
}
