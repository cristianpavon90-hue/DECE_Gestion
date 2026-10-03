package com.example.decegestin

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentBitacoraBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.skydoves.balloon.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Módulo de Bitácora v1.3: Seguimiento secuencial de comisiones.
 * Permite iniciar con Salida Biométrico y completar pasos progresivamente.
 */
class BitacoraFragment : Fragment() {

    private var _binding: FragmentBitacoraBinding? = null
    private val binding get() = _binding!!

    private val recordsList = mutableListOf<JSONObject>()
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBitacoraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applyTopBarPadding()

        setupUI()
        setupRecyclerView()
        loadRecords()
        
        // Mostrar tutorial si es la primera vez o al tocar el icono
        binding.btnTutorial.setOnClickListener { startTutorial() }
        
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("BitacoraPrefs_$uid", Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("tutorial_shown", true)
        if (isFirstTime) {
            binding.root.postDelayed({
                if (_binding != null) startTutorial()
                prefs.edit(commit = false) { putBoolean("tutorial_shown", false) }
            }, 500)
        }
    }

    private fun startTutorial() {
        val balloon1 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.3f)
            setWidthRatio(0.7f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.bitacora_tut_step1_msg))
            setTextColorResource(R.color.white)
            setTextSize(15f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val balloon2 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.bitacora_tut_step2_msg))
            setTextColorResource(R.color.white)
            setTextSize(15f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val balloon3 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.bitacora_tut_step3_msg))
            setTextColorResource(R.color.white)
            setTextSize(15f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        balloon1.showAlignBottom(binding.layoutBioExit)
        balloon1.setOnBalloonDismissListener {
            if (_binding != null) {
                balloon2.showAlignBottom(binding.recyclerRecords)
            }
        }
        balloon2.setOnBalloonDismissListener {
            if (_binding != null) {
                balloon3.showAlignTop(binding.recyclerRecords)
            }
        }
    }

    private fun setupUI() {
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        // Fecha Actual
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.textCurrentDate.text = sdf.format(Date())

        binding.editBioExit.setOnClickListener { showTimePicker { time -> binding.editBioExit.setText(time) } }

        binding.buttonAddRecord.setOnClickListener {
            val bioExit = binding.editBioExit.text.toString()
            if (bioExit.isNotEmpty()) {
                createNewRecord(bioExit)
            } else {
                showToast(getString(R.string.bitacora_error_bio))
            }
        }
    }

    private fun createNewRecord(bioExit: String) {
        val newObj = JSONObject().apply {
            put("date", binding.textCurrentDate.text.toString())
            put("bioExit", bioExit)
            put("commArrival", "")
            put("commExit", "")
            put("bioArrival", "")
        }
        recordsList.add(0, newObj)
        saveRecords()
        binding.recyclerRecords.adapter?.notifyItemInserted(0)
        binding.recyclerRecords.scrollToPosition(0)
        binding.editBioExit.text?.clear()
        showToast(getString(R.string.bitacora_started))
    }

    private fun showTimePicker(onTimeSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m ->
            onTimeSelected(String.format(Locale.getDefault(), "%02d:%02d", h, m))
        }, calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE], true).show()
    }

    private fun setupRecyclerView() {
        binding.recyclerRecords.layoutManager = LinearLayoutManager(context)
        binding.recyclerRecords.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val record = recordsList[position]
                (holder.itemView as android.widget.TextView).apply {
                    val date = record.optString("date")
                    val bS = record.optString("bioExit")
                    val cLl = record.optString("commArrival").ifEmpty { "--:--" }
                    val cS = record.optString("commExit").ifEmpty { "--:--" }
                    val bLl = record.optString("bioArrival").ifEmpty { "--:--" }
                    
                    text = getString(R.string.bitacora_record_format, date, bS, cLl, cS, bLl)
                    textSize = 12f
                    setPadding(32, 32, 32, 32)
                    
                    setOnClickListener { handleRecordTap(position, record) }
                    setOnLongClickListener {
                        showOptionsDialog(position, record)
                        true
                    }
                }
            }

            override fun getItemCount(): Int = recordsList.size
        }
    }

    private fun handleRecordTap(position: Int, record: JSONObject) {
        val cLl = record.optString("commArrival")
        val cS = record.optString("commExit")
        val bLl = record.optString("bioArrival")

        val nextStepRes = when {
            cLl.isEmpty() -> R.string.bitacora_comm_arrival
            cS.isEmpty() -> R.string.bitacora_comm_exit
            bLl.isEmpty() -> R.string.bitacora_bio_arrival
            else -> {
                showToast(getString(R.string.bitacora_completed))
                return
            }
        }
        
        val nextStepName = getString(nextStepRes)

        showTimePicker { time ->
            when (nextStepRes) {
                R.string.bitacora_comm_arrival -> record.put("commArrival", time)
                R.string.bitacora_comm_exit -> record.put("commExit", time)
                R.string.bitacora_bio_arrival -> record.put("bioArrival", time)
            }
            saveRecords()
            binding.recyclerRecords.adapter?.notifyItemChanged(position)
            showToast(getString(R.string.bitacora_step_registered, nextStepName))
        }
    }

    private fun showOptionsDialog(position: Int, record: JSONObject) {
        val options = arrayOf(getString(R.string.edit), getString(R.string.delete))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_settings)
            .setItems(options) { _, which ->
                if (which == 0) showEditMenu(position, record)
                else confirmDelete(position)
            }
            .show()
    }

    private fun showEditMenu(position: Int, record: JSONObject) {
        val times = arrayOf(
            "${getString(R.string.bitacora_bio_exit)} (${record.optString("bioExit")})",
            "${getString(R.string.bitacora_comm_arrival)} (${record.optString("commArrival").ifEmpty { "N/A" }})",
            "${getString(R.string.bitacora_comm_exit)} (${record.optString("commExit").ifEmpty { "N/A" }})",
            "${getString(R.string.bitacora_bio_arrival)} (${record.optString("bioArrival").ifEmpty { "N/A" }})"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit)
            .setItems(times) { _, which ->
                showTimePicker { newTime ->
                    when (which) {
                        0 -> record.put("bioExit", newTime)
                        1 -> record.put("commArrival", newTime)
                        2 -> record.put("commExit", newTime)
                        3 -> record.put("bioArrival", newTime)
                    }
                    saveRecords()
                    binding.recyclerRecords.adapter?.notifyItemChanged(position)
                    showToast(getString(R.string.bitacora_updated))
                }
            }
            .show()
    }

    private fun confirmDelete(position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.warning)
            .setMessage(R.string.bitacora_confirm_delete)
            .setPositiveButton(R.string.delete) { _, _ ->
                recordsList.removeAt(position)
                saveRecords()
                binding.recyclerRecords.adapter?.notifyItemRemoved(position)
                showToast(getString(R.string.bitacora_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadRecords() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("BitacoraPrefs_$uid", Context.MODE_PRIVATE)
        val json = prefs.getString("records", "[]")
        try {
            val arr = JSONArray(json)
            recordsList.clear()
            for (i in 0 until arr.length()) {
                recordsList.add(arr.getJSONObject(i))
            }
            binding.recyclerRecords.adapter?.notifyDataSetChanged()
        } catch (e: Exception) {}
    }

    private fun saveRecords() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("BitacoraPrefs_$uid", Context.MODE_PRIVATE)
        val arr = JSONArray()
        recordsList.forEach { arr.put(it) }
        prefs.edit { putString("records", arr.toString()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
