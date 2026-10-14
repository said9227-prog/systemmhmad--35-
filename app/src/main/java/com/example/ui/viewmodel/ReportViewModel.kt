package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.Client
import com.example.data.model.Item
import com.example.data.repository.AppRepository
import com.example.domain.report.DateFilterType
import com.example.domain.report.DateRange
import com.example.domain.report.ReportData
import com.example.domain.report.ReportDateUtils
import com.example.domain.report.ReportEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

class ReportViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    private val _selectedReportCurrency = MutableStateFlow("كل العملات")
    val selectedReportCurrency: StateFlow<String> = _selectedReportCurrency

    private val _selectedDateFilter = MutableStateFlow(DateFilterType.THIS_MONTH)
    val selectedDateFilter: StateFlow<DateFilterType> = _selectedDateFilter

    private val _customDateRange = MutableStateFlow(ReportDateUtils.getRange(DateFilterType.THIS_MONTH))
    val customDateRange: StateFlow<DateRange> = _customDateRange

    val storeSettings = repository.getSettingsFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val reportData: StateFlow<ReportData?> = combine(
        _selectedReportCurrency,
        _selectedDateFilter,
        _customDateRange
    ) { currency, filterType, customRange ->
        Triple(currency, filterType, customRange)
    }.mapLatest { (currency, filterType, customRange) ->
        val dateRange = if (filterType == DateFilterType.CUSTOM) customRange else ReportDateUtils.getRange(filterType)
        
        withContext(Dispatchers.IO) {
            val invoices = repository.getInvoicesByDateRange(dateRange.start, dateRange.end)
            val draftInvoices = repository.getDraftInvoicesByDateRange(dateRange.start, dateRange.end)
            val allInvoicesForRange = invoices + draftInvoices
            val payments = repository.getPaymentsByDateRange(dateRange.start, dateRange.end)
            val returns = repository.getReturnsByDateRange(dateRange.start, dateRange.end)
            val unpaidInvoices = repository.getAllUnpaidInvoices()
            val invoiceIds = (allInvoicesForRange + unpaidInvoices).map { it.id }.distinct()
            val invoiceItems = repository.getInvoiceItemsForInvoices(invoiceIds)
            
            val clientInfos = repository.getClientInfos()
            val itemInfos = repository.getItemInfos()
            val installments = repository.getAllInstallments()
            val suppliers = repository.getAllCompanies()
            val purchaseHistory = repository.getAllPurchaseHistory()

            ReportEngine.generateReport(
                dateRange = dateRange,
                selectedCurrency = currency,
                invoices = allInvoicesForRange + unpaidInvoices.filter { it !in allInvoicesForRange },
                invoiceItems = invoiceItems,
                payments = payments,
                clients = clientInfos.map { Client(id = it.id, name = it.name, currency = it.currency) },
                items = itemInfos.map { Item(id = it.id, name = it.name, purchaseCurrency = it.purchaseCurrency) },
                installments = installments,
                suppliers = suppliers,
                returns = returns,
                purchaseHistory = purchaseHistory
            )
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setReportCurrency(currency: String) {
        _selectedReportCurrency.value = currency
    }

    fun setReportDateFilter(type: DateFilterType) {
        _selectedDateFilter.value = type
        if (type != DateFilterType.CUSTOM) {
             _customDateRange.value = ReportDateUtils.getRange(type)
        }
    }

    fun setReportCustomDateRange(start: Long, end: Long) {
        _customDateRange.value = DateRange(start, end)
        _selectedDateFilter.value = DateFilterType.CUSTOM
    }
}

class ReportViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
