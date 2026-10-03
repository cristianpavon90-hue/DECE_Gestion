package com.example.decegestin

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentSexualViolenceDbBinding
import com.example.decegestin.databinding.ItemSexualViolenceStudentBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.skydoves.balloon.*

class SexualViolenceDbFragment : Fragment() {

    private var _binding: FragmentSexualViolenceDbBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val studentsList = mutableListOf<Map<String, Any>>()
    private lateinit var adapter: StudentAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSexualViolenceDbBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topBar.applyTopBarPadding()
        binding.menuIcon.setOnClickListener { (activity as? MainActivity)?.openDrawer() }

        setupRecyclerView()
        loadStudents()

        binding.btnTutorial.setOnClickListener { startTutorial() }
        checkFirstTimeTutorial()
    }

    private fun checkFirstTimeTutorial() {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("SexDbTutorialPrefs_$uid", Context.MODE_PRIVATE)
        val isFirstTime = prefs.getBoolean("tutorial_shown", true)
        if (isFirstTime) {
            binding.root.postDelayed({
                if (_binding != null) startTutorial()
                prefs.edit { putBoolean("tutorial_shown", false) }
            }, 800)
        }
    }

    private fun startTutorial() {
        val b1 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_sex_db_step1))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        val b2 = createBalloon(requireContext()) {
            setArrowSize(10)
            setArrowOrientation(ArrowOrientation.TOP)
            setArrowPosition(0.5f)
            setWidthRatio(0.8f)
            setHeight(BalloonSizeSpec.WRAP)
            setText(getString(R.string.tut_sex_db_step2))
            setTextColorResource(R.color.white)
            setTextSize(14f)
            setBackgroundColorResource(R.color.md_theme_primary)
            setBalloonAnimation(BalloonAnimation.ELASTIC)
            setLifecycleOwner(viewLifecycleOwner)
            setDismissWhenClicked(true)
        }

        b1.showAlignBottom(binding.recyclerStudents)
        b1.setOnBalloonDismissListener {
            if (_binding != null) b2.showAlignBottom(binding.recyclerStudents)
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentAdapter()
        binding.recyclerStudents.layoutManager = LinearLayoutManager(context)
        binding.recyclerStudents.adapter = adapter
    }

    private fun loadStudents() {
        val user = auth.currentUser ?: return
        binding.progressBar.visibility = View.VISIBLE

        database.child("users").child(user.uid).child("institution").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            val inst = snapshot.value?.toString() ?: return@addOnSuccessListener
            val safeKey = AppUtils.getSafeKey(inst)

            database.child("institutions").child(safeKey).child("forms").get().addOnSuccessListener { formsSnapshot ->
                if (_binding == null) return@addOnSuccessListener
                binding.progressBar.visibility = View.GONE
                studentsList.clear()

                for (child in formsSnapshot.children) {
                    val data = child.value as? Map<String, Any> ?: continue
                    val caseType = data["caseType"]?.toString() ?: ""
                    if (caseType == "Violencia sexual") {
                        val mutable = data.toMutableMap()
                        mutable["formId"] = child.key ?: ""
                        studentsList.add(mutable)
                    }
                }

                binding.textEmptyStudents.visibility = if (studentsList.isEmpty()) View.VISIBLE else View.GONE
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class StudentAdapter : RecyclerView.Adapter<StudentAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemSexualViolenceStudentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(studentsList[position])
        }

        override fun getItemCount() = studentsList.size

        inner class ViewHolder(val b: ItemSexualViolenceStudentBinding) : RecyclerView.ViewHolder(b.root) {
            fun bind(item: Map<String, Any>) {
                val studentName = item["studentName"]?.toString() ?: "Estudiante"
                val grado = item["grado"]?.toString() ?: ""
                val paralelo = item["paralelo"]?.toString() ?: ""
                val formId = item["formId"]?.toString() ?: ""

                b.textStudentName.text = studentName
                b.textStudentGrade.text = "$grado \"$paralelo\""

                val extras = item["sexualViolenceExtras"] as? Map<String, String>
                if (extras != null) {
                    val repName = extras["repName"] ?: ""
                    val repPhone = extras["repPhone"] ?: ""
                    if (repName.isNotEmpty()) {
                        b.textRepInfo.text = "Rep: $repName ($repPhone)"
                        b.textRepInfo.visibility = View.VISIBLE
                    }
                } else {
                    b.textRepInfo.visibility = View.GONE
                }

                b.root.setOnClickListener {
                    val bundle = Bundle().apply {
                        putString("formId", formId)
                        putString("studentName", studentName)
                        putString("grado", grado)
                        putString("paralelo", paralelo)
                    }
                    findNavController().navigate(R.id.action_SexualViolenceDbFragment_to_SexualViolenceDetailFragment, bundle)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
