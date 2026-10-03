package com.example.decegestin

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.decegestin.databinding.FragmentPdfViewerBinding
import com.github.chrisbanes.photoview.PhotoView
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import kotlin.concurrent.thread

class PdfViewerFragment : Fragment() {

    private var _binding: FragmentPdfViewerBinding? = null
    private val binding get() = _binding!!

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfFile: File? = null
    private var pdfName: String = ""

    private var renderScale = 2.0f
    private var thumbnailScale = 0.5f
    private var isNightMode = false

    private val pageTexts = mutableListOf<String>()
    private var matchPages = listOf<Int>()
    private var currentMatchIndex = -1
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
        binding.topBar.applyTopBarPadding()

        val pdfUrl = arguments?.getString("pdfUrl") ?: ""
        pdfName = arguments?.getString("pdfName") ?: "Documento"

        binding.pdfTitleTop.text = pdfName
        binding.gridTitle.text = pdfName
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

        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(v.text.toString())
                true
            } else false
        }

        binding.btnSearchNext.setOnClickListener { goToMatch(currentMatchIndex + 1) }
        binding.btnSearchPrev.setOnClickListener { goToMatch(currentMatchIndex - 1) }

        binding.btnGridView.setOnClickListener {
            binding.gridViewLayout.visibility = View.VISIBLE
        }

        binding.btnCloseGrid.setOnClickListener {
            binding.gridViewLayout.visibility = View.GONE
        }

        binding.btnPrint.setOnClickListener {
            pdfFile?.let { printPdf(it) }
        }

        binding.btnThemeToggle.setOnClickListener {
            isNightMode = !isNightMode
            updateTheme()
        }
    }

    private fun updateTheme() {
        val bgColor = if (isNightMode) android.graphics.Color.BLACK else android.graphics.Color.parseColor("#F2F2F2")
        binding.pdfRecyclerView.setBackgroundColor(bgColor)
        binding.pdfRecyclerView.adapter?.notifyDataSetChanged()
        
        // El icono de flecha cambia ligeramente su apariencia para indicar el estado
        binding.btnThemeToggle.setImageResource(
            if (isNightMode) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
    }

    private fun printPdf(file: File) {
        val printManager = requireContext().getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
        val jobName = "${getString(R.string.app_name)} - $pdfName"
        
        val printAdapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val pbi = android.print.PrintDocumentInfo.Builder(pdfName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(pbi, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val input = FileInputStream(file)
                    val output = FileOutputStream(destination?.fileDescriptor)
                    input.copyTo(output)
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }

        printManager.print(jobName, printAdapter, null)
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
                pdfFile = file

                extractText(file)

                activity?.runOnUiThread {
                    if (_binding == null) return@runOnUiThread
                    binding.progressBar.visibility = View.GONE
                    setupPdfRenderer(file)
                    updateTheme() // Aplicar tema inicial
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
        } catch (e: Exception) {}
    }

    private fun runSearch(query: String) {
        val q = query.trim().lowercase()
        if (q.isEmpty() || pageTexts.isEmpty()) return

        lastQuery = q
        matchPages = pageTexts.indices.filter { pageTexts[it].contains(q) }

        currentMatchIndex = -1
        if (matchPages.isNotEmpty()) {
            goToMatch(0)
        } else {
            showToast(getString(R.string.docs_no_matches))
        }
    }

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

        binding.textSearchResult.text = getString(R.string.page_count, currentMatchIndex + 1, matchPages.size)
    }

    private fun setupPdfRenderer(file: File) {
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor!!)

            binding.pdfRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.pdfRecyclerView.adapter = PdfPageAdapter(false)

            binding.gridRecyclerView.layoutManager = GridLayoutManager(requireContext(), 3)
            binding.gridRecyclerView.adapter = PdfPageAdapter(true)

        } catch (e: Exception) {
            showToast(getString(R.string.error_occurred))
        }
    }

    private inner class PdfPageAdapter(val isThumbnail: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val nightModeFilter: ColorMatrixColorFilter by lazy {
            val matrix = ColorMatrix(floatArrayOf(
                -1f,  0f,  0f, 0f, 255f,
                 0f, -1f,  0f, 0f, 255f,
                 0f,  0f, -1f, 0f, 255f,
                 0f,  0f,  0f, 1f,   0f
            ))
            ColorMatrixColorFilter(matrix)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (isThumbnail) {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_thumbnail, parent, false)
                ThumbnailViewHolder(view)
            } else {
                val photoView = PhotoView(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                PageViewHolder(photoView)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val renderer = pdfRenderer ?: return
            val page = renderer.openPage(position)

            val scale = if (isThumbnail) thumbnailScale else renderScale
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            if (holder is PageViewHolder) {
                val photoView = holder.itemView as PhotoView
                photoView.setScale(1f, false)
                photoView.setImageBitmap(bitmap)
                
                // Aplicar filtro de inversión solo en modo lectura si isNightMode está activo
                photoView.colorFilter = if (isNightMode) nightModeFilter else null
                
            } else if (holder is ThumbnailViewHolder) {
                holder.thumbnail.setImageBitmap(bitmap)
                holder.thumbnail.colorFilter = if (isNightMode) nightModeFilter else null
                holder.pageNumber.text = (position + 1).toString()
                holder.itemView.setOnClickListener {
                    binding.gridViewLayout.visibility = View.GONE
                    binding.pdfRecyclerView.scrollToPosition(position)
                }
            }
            page.close()
        }

        override fun getItemCount(): Int = pdfRenderer?.pageCount ?: 0

        inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view)
        inner class ThumbnailViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val thumbnail: ImageView = view.findViewById(R.id.img_thumbnail)
            val pageNumber: TextView = view.findViewById(R.id.tv_page_number)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pdfRenderer?.close()
        fileDescriptor?.close()
        _binding = null
    }
}