package com.example.myapp2.client

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
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
import com.example.myapp2.Client
import com.example.myapp2.ClientsResponse
import com.example.myapp2.R
import com.example.myapp2.RetrofitClient
import com.example.myapp2.TableAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A simple [Fragment] subclass.
 * Use the [ClientsList.newInstance] factory method to
 * create an instance of this fragment.
 */
class ClientsList : Fragment(), ClientAdapter.OnItemClickedDB,
    ClientAdapter.OnSaveClick {

    private lateinit var repository: TableAdapter
    private val apiService = RetrofitClient.api
    private lateinit var recyclerView: RecyclerView
    private lateinit var clientAdapter: ClientAdapter
    private val clientList = mutableListOf<Client>()
    private var isLoading = false
    private var hasMoreData = true
    private var currentPage = 1

    private lateinit var searchView: SearchView
    private var currentSearchQuery: String? = null

    private lateinit var progressBar: ProgressBar
    private val vm by viewModels<ClientsListVM>()


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
        val view = inflater.inflate(R.layout.fragment_clients_list, container, false)
        val addButton: Button = view.findViewById(R.id.addClientButton)
        searchView = view.findViewById(R.id.searchView)
        val goBackButton: Button = view.findViewById(R.id.goBack)
        val statisticsButton: Button = view.findViewById(R.id.statisticsButton)

        progressBar = view.findViewById(R.id.progressBar)

        recyclerView = view.findViewById(R.id.recyclerViewClients)
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        clientAdapter = ClientAdapter(clientList)
        clientAdapter.setOnClickDB(this@ClientsList)
        clientAdapter.setOnSaveClick(this@ClientsList)
        recyclerView.adapter = clientAdapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMoreData) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0) {
                        loadClients()
                    }
                }
            }
        })

        addButton.setOnClickListener {
            findNavController().navigate(R.id.action_clientsList_to_addClientForm)
        }
        goBackButton.setOnClickListener {
            findNavController().navigate(R.id.action_clientsList_to_activityHub)
        }

        statisticsButton.setOnClickListener {
            showStatisticsDialog()
        }

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                filterClients(newText)
                return true
            }
        })

        vm.clientsResponse.observe(viewLifecycleOwner) { response ->
            if (response != null) {
                updateRecyclerView(response)
            }
        }

        return view
    }

    private fun showStatisticsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Вывести статистику?")
            .setPositiveButton("Да") { _, _ ->
                loadViolationStatistics()
            }
            .setNegativeButton("Нет", null)
            .show()
    }


    private fun loadViolationStatistics() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val clientCount =  vm.getTotalClientCount()
                val violations =  apiService.getViolations()
                val violationCounts = violations.groupingBy { it.client_id }.eachCount()
                val sortedViolators = violationCounts.entries.sortedByDescending { it.value }.take(10)
                val resultString = buildString {
                    append("Общее количество клиентов: $clientCount\n\n")
                    append("Топ нарушителей:\n")
                    sortedViolators.forEach { (clientId, count) ->
                        append("Client ID: $clientId, Нарушений: $count\n")
                    }
                }
                withContext(Dispatchers.Main){
                    showStatisticsResult(resultString)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorDialog("Ошибка при загрузке статистики: ${e.message}")
                }
            }
        }
    }

    private fun showStatisticsResult(resultString: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Статистика")
            .setMessage(resultString)
            .setPositiveButton("Ок", null)
            .show()
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Ошибка")
            .setMessage(message)
            .setPositiveButton("Ок", null)
            .show()
    }


    private fun filterClients(query: String?) {
        clientList.clear()
        currentPage = 1
        hasMoreData = true
        loadClients()
    }

    private fun loadClients() {
        Log.d("ClientsList", "loadClients() called. Current page: $currentPage")
        isLoading = true
        progressBar?.visibility = View.VISIBLE
        vm.getClients(currentPage, currentSearchQuery)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateRecyclerView(response: ClientsResponse) {
        Log.d("ClientsList", "updateRecyclerView() called. Current page: $currentPage")
        val newClients = response.clients
        if (newClients.isEmpty()) {
            hasMoreData = false
            Log.d("ClientsList", "No more data. hasMoreData = $hasMoreData")
        } else {
            clientList.addAll(newClients)
            clientAdapter.notifyDataSetChanged()
            currentPage++
            Log.d(
                "ClientsList",
                "Loaded new data, current page is incremented. Current page: $currentPage"
            )
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
        vm.removeClient(id)
        clientList.clear()
        currentPage = 1
    }

    override fun onSaveClick(id: Int, client: Client) {
        vm.editClient(id, client)
        clientList.clear()
        currentPage = 1
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ClientsList.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ClientsList().apply {
                /*arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }*/
            }
    }
}