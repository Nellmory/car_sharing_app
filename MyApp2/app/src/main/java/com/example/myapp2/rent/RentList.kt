package com.example.myapp2.rent

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapp2.R
import com.example.myapp2.Rent
import com.example.myapp2.RentsResponse
import com.example.myapp2.TableAdapter
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A simple [Fragment] subclass.
 * Use the [RentList.newInstance] factory method to
 * create an instance of this fragment.
 */
class RentList : Fragment(), RentAdapter.OnItemClickedDB,
    RentAdapter.OnSaveClick {

    private lateinit var recyclerView: RecyclerView
    private lateinit var rentAdapter: RentAdapter
    private val rentList = mutableListOf<Rent>()
    private var isLoading = false
    private var hasMoreData = true
    private var currentPage = 1
    private var startDateFilter: String? = null
    private var finishDateFilter: String? = null
    private lateinit var searchView: SearchView
    private var currentSearchQuery: String? = null
    private val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val inputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private lateinit var progressBar: ProgressBar
    private val vm by viewModels<RentListVM>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /*arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }*/
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_rent_list, container, false)
        val addButton: Button = view.findViewById(R.id.addRentButton)
        val goBackButton: Button = view.findViewById(R.id.goBack)
        val filterButton: Button = view.findViewById(R.id.filterButton)
        searchView = view.findViewById(R.id.searchView)

        progressBar = view.findViewById(R.id.progressBarR)

        recyclerView = view.findViewById(R.id.recyclerViewRents)
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        rentAdapter = RentAdapter(rentList)
        rentAdapter.setOnClickDB(this@RentList)
        rentAdapter.setOnSaveClick(this@RentList)
        recyclerView.adapter = rentAdapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadRents(null, startDateFilter, finishDateFilter)
                    }
                }
            }
        })

        addButton.setOnClickListener {
            findNavController().navigate(R.id.action_rentList_to_addRentForm)
        }
        goBackButton.setOnClickListener {
            findNavController().navigate(R.id.action_rentList_to_activityHub)
        }
        filterButton.setOnClickListener {
            showDateRangePicker()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                filterRents(newText)
                return true
            }
        })

        vm.rentsResponse.observe(viewLifecycleOwner) { response ->
            if (response != null) {
                updateRecyclerView(response)
            }
        }
        return view
    }

    private fun showDateRangePicker() {
        val builder = MaterialDatePicker.Builder.dateRangePicker()
        val picker = builder.build()

        Log.e("RentList", "showDateRangePicker() was called")
        picker.addOnPositiveButtonClickListener { selection ->
            val startDate = Date(selection.first)
            val endDate = Date(selection.second)
            startDateFilter = outputFormat.format(startDate)
            finishDateFilter = outputFormat.format(endDate)
            Log.e("RentList", "startDate: ${startDateFilter}, finishDate:${finishDateFilter}")
            filterRents(currentSearchQuery, startDateFilter, finishDateFilter)
        }

        picker.show(childFragmentManager, picker.toString())
    }


    private fun filterRents(query: String?, startDate: String? = null, finishDate: String? = null) {
        rentList.clear()
        currentPage = 1
        hasMoreData = true
        Log.e("RentList", " filter rents was called")
        loadRents(query, startDate, finishDate)
    }

    private fun loadRents(searchQuery: String? = null, startDate: String? = null, finishDate: String? = null) {
        Log.d("RentsList", "loadRents() called. Current page: $currentPage. searchQuery: $searchQuery, startDate: $startDate, finishDate: $finishDate")
        isLoading = true
        progressBar.visibility = View.VISIBLE

        val formattedQuery = formatQuery(searchQuery)
        vm.getRents(currentPage, formattedQuery, startDate, finishDate)
    }

    private fun formatQuery(query: String?): String? {
        if (query.isNullOrEmpty()) return null

        return try {
            val date = inputFormat.parse(query)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            query
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView(response: RentsResponse) {
        Log.d("RentList", "updateRecyclerView() called. Current page: $currentPage")
        val newRents = response.rents
        if (newRents.isEmpty()) {
            hasMoreData = false
            Log.d("RentList", "No more data. hasMoreData = $hasMoreData")
        } else {
            rentList.addAll(newRents)
            rentAdapter.notifyDataSetChanged()
            currentPage++
            Log.d("RentList", "Loaded new data, current page is incremented. Current page: $currentPage")
        }
        hasMoreData = response.has_next
        Log.d("API", "Current page: " + response.current_page)
        Log.d("API", "Total pages: " + response.total_pages)
        Log.d("API", "Has next: " + response.has_next)
        Log.d("API", "Has prev: " + response.has_prev)

        isLoading = false
        progressBar.visibility = View.GONE
    }

    override fun onDeleteButtonClick(id: Int) {
        vm.removeRent(id)
        rentList.clear()
        currentPage = 1
    }

    override fun onSaveClick(id: Int, rent: Rent) {
        vm.editRent(id,rent)
        rentList.clear()
        currentPage = 1
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment RentList.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            RentList().apply {
                /*arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }*/
            }
    }
}