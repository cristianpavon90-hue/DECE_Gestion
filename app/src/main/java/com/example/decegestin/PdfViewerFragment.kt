package com.example.decegestin

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentPdfViewerBinding
import com.github.chrisbanes.photoview.PhotoView
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import kotlin.concurrent.thread

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfName: String = ""

    // --- ZOOM ---
    // zoomLevel solo controla la RESOLUCIÓN de render (calidad del bitmap),
    // no el zoom visual. El zoom visual real lo maneja PhotoView (gestos + botones lo controlan
    // a través de su propio setScale).
    private var renderScale = 2.0f

    // --- BÚSQUEDA ---
    private val pageTexts = mutableListOf<String>()
    private var matchPages = listOf<Int>()      // páginas (índices) donde hay coincidencias
    private var currentMatchIndex = -1          // posición actual dentro de matchPages
    private var lastQuery = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPdfViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pdfUrl = arguments?.getString("pdfUrl") ?: ""
        pdfName = arguments?.getString("pdfName") ?: "Documento"

        binding.pdfTitleTop.text = pdfName
        binding.backIcon.setOnClickListener { findNavController().navigateUp() }

        setupUI()

        if (pdfUrl.isNotEmpty()) {
            downloadAndRenderPdf(pdfUrl)
        }
    }

    private fun setupUI() {
        binding.btnSearchToggle.setOnClickListener {
            binding.searchBarLayout.visibility =
                if (binding.searchBarLayout.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // --- Zoom con botones: ahora actúa sobre la página VISIBLE actual,
        // usando PhotoView.setScale(), no sobre el bitmap completo.
        binding.btnZoomIn.setOnClickListener { zoomCurrentPage(by = 1.25f) }
        binding.btnZoomOut.setOnClickListener { zoomCurrentPage(by = 0.8f) }

        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(v.text.toString())
                true
            } else false
        }

        // Botones siguiente / anterior coincidencia (deben existir en el layout,
        // ver nota al final sobre el XML)
        binding.btnSearchNext.setOnClickListener { goToMatch(currentMatchIndex + 1) }
        binding.btnSearchPrev.setOnClickListener { goToMatch(currentMatchIndex - 1) }
    }

    private fun zoomCurrentPage(by: Float) {
        val lm = binding.pdfRecyclerView.layoutManager as? LinearLayoutManager ?: return
        val position = lm.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return

        val holder = binding.pdfRecyclerView.findViewHolderForAdapterPosition(position)
                as? PdfPageAdapter.PageViewHolder ?: return

        val photoView = holder.itemView as PhotoView
        val newScale = (photoView.scale * by).coerceIn(1f, 6f)
        photoView.setScale(newScale, true)
    }

    private fun downloadAndRenderPdf(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        thread {
            try {
                val directUrl = if (url.contains("drive.google.com")) {
                    val fileId = url.split("/d/").getOrNull(1)?.split("/")?.getOrNull(0)
                        ?: url.split("id=").getOrNull(1)?.split("&")?.getOrNull(0)
                    if (fileId != null) "https://drive.google.com/uc?export=download&id=$fileId" else url
                } else {
                    url
                }

                val file = File(requireContext().cacheDir, "pdf_${pdfName.hashCode()}.pdf")
                if (!file.exists()) {
                    URL(directUrl).openStream().use { input ->
                        FileOutputStream(file).use { output -> input.copyTo(output) }
                    }
                }

                extractText(file)

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.progressBar.visibility = View.GONE
                    setupPdfRenderer(file)
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.progressBar.visibility = View.GONE
                    showToast("${getString(R.string.error_occurred)}: ${e.message}")
                }
            }
        }
    }

    private fun extractText(file: File) {
        try {
            val reader = PdfReader(file.absolutePath)
            pageTexts.clear()
            for (i in 1..reader.numberOfPages) {
                val text = PdfTextExtractor.getTextFromPage(reader, i)
                pageTexts.add(text.lowercase())
            }
            reader.close()
        } catch (e: Exception) {
            // Considera loggear el error en vez de tragarlo silenciosamente
        }
    }

    // --- BÚSQUEDA NAVEGABLE ---

    private fun runSearch(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty() || pageTexts.isEmpty()) return

        lastQuery = q
        matchPages = pageTexts.indices.filter { pageTexts[it].contains(q) }

        val totalMatches = pageTexts.sumOf { page -> countOccurrences(page, q) }
        binding.textSearchResult.text = if (matchPages.isEmpty()) {
            getString(R.string.docs_no_matches)
        } else {
            val totalPages = matchPages.size
            "${currentMatchIndex + 1} / $totalPages ($totalMatches)"
        }

        currentMatchIndex = -1
        if (matchPages.isNotEmpty()) {
            goToMatch(0)
        } else {
            showToast(getString(R.string.docs_no_matches))
        }
    }

    private fun countOccurrences(text: String, query: String): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(query)
        while (index != -1) {
            count++
            index = text.indexOf(query, index + query.length)
        }
        return count
    }

    /** Navega a la coincidencia en la posición [index] dentro de matchPages, con wrap-around. */
    private fun goToMatch(index: Int) {
        if (matchPages.isEmpty()) return

        currentMatchIndex = when {
            index < 0 -> matchPages.size - 1
            index >= matchPages.size -> 0
            else -> index
        }

        val targetPage = matchPages[currentMatchIndex]
        (binding.pdfRecyclerView.layoutManager as LinearLayoutManager)
            .scrollToPositionWithOffset(targetPage, 0)

        binding.textSearchResult.text = "${currentMatchIndex + 1} / ${matchPages.size}"
    }

    private fun setupPdfRenderer(file: File) {
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.pdfRecyclerView.setHasFixedSize(false)
            binding.pdfRecyclerView.adapter = PdfPageAdapter()

        } catch (e: Exception) {
            showToast(getString(R.string.error_occurred))
        }
    }

    private inner class PdfPageAdapter : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            // PhotoView reemplaza a ImageView: trae pinch-to-zoom y doble-toque listos.
            val photoView = PhotoView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                minimumScale = 1f
                maximumScale = 6f
                // Evita conflicto con el scroll vertical del RecyclerView cuando
                // el usuario hace zoom y luego intenta hacer panning dentro de la página.
                setOnScaleChangeListener { _, _, _ ->
                    binding.pdfRecyclerView.suppressLayout(false)
                }
            }
            return PageViewHolder(photoView)
        }

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            val renderer = pdfRenderer ?: return
            val photoView = holder.itemView as PhotoView

            // Resetea el zoom al reciclar la vista, para que no "herede" el zoom
            // de una página anterior.
            photoView.setScale(1f, false)

            val page = renderer.openPage(position)

            val width = (page.width * renderScale).toInt().coerceAtLeast(1)
            val height = (page.height * renderScale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            photoView.setImageBitmap(bitmap)
            page.close()
        }

        override fun getItemCount(): Int = pdfRenderer?.pageCount ?: 0

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pdfRenderer?.close()
        fileDescriptor?.close()
        _binding = null
    }
}