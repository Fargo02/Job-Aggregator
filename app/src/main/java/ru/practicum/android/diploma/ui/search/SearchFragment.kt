package ru.practicum.android.diploma.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity.INPUT_METHOD_SERVICE
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnScrollListener
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.practicum.android.diploma.R
import ru.practicum.android.diploma.common.AppConstants.CLICK_DEBOUNCE_DELAY
import ru.practicum.android.diploma.databinding.FragmentSearchBinding
import ru.practicum.android.diploma.domain.models.Vacancy
import ru.practicum.android.diploma.domain.state.VacancyState
import ru.practicum.android.diploma.domain.state.VacancyState.VacanciesList.Data
import ru.practicum.android.diploma.ui.vacancy.VacancyFragment
import ru.practicum.android.diploma.util.BindingFragment
import ru.practicum.android.diploma.util.ImageAndTextHelper
import ru.practicum.android.diploma.util.debounce
import ru.practicum.android.diploma.util.invisible
import ru.practicum.android.diploma.util.visible

class SearchFragment : BindingFragment<FragmentSearchBinding>(), VacanciesAdapter.VacancyClickListener {

    private var vacancyAdapter = VacanciesAdapter(this)
    private val viewModel: SearchViewModel by viewModel()
    private val imageAndTextHelper: ImageAndTextHelper by inject()

    private lateinit var onTrackClickDebounce: (String) -> Unit

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ) = FragmentSearchBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureRecycler()
        configureSearchInput()

        onTrackClickDebounce = debounce(
            CLICK_DEBOUNCE_DELAY,
            viewLifecycleOwner.lifecycleScope,
            false
        ) { vacancyId ->
            findNavController().navigate(
                R.id.action_searchFragment_to_vacancyFragment,
                VacancyFragment.createArgs(id = vacancyId)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { state ->
                render(state)
            }
        }
    }

    override fun onVacancyClick(vacancyId: String) {
        onTrackClickDebounce(vacancyId)
    }

    private fun render(state: VacancyState) {
        when (state.vacanciesList) {
            is VacancyState.VacanciesList.Empty -> showEmpty()
            is VacancyState.VacanciesList.NoInternet -> showNoInternet()
            is VacancyState.VacanciesList.Loading -> showLoading()
            is VacancyState.VacanciesList.Error -> showError()
            is Data -> showContent(state.vacanciesList.vacancies)
        }
    }

    private fun showEmpty() {
        with(binding) {
            pbSearch.invisible()
            rvVacancies.invisible()
            ivLookingForPlaceholder.visible()
            groupPlaceholder.invisible()
            tvCountVacancies.invisible()
        }
    }

    private fun showNoInternet() {
        with(binding) {
            rvVacancies.invisible()
            pbSearch.invisible()
            ivLookingForPlaceholder.invisible()
            groupPlaceholder.visible()
            tvCountVacancies.invisible()
            imageAndTextHelper.setImageAndText(
                requireContext(),
                ivPlaceholder,
                tvPlaceholder,
                R.drawable.placeholder_vacancy_search_no_internet_skull,
                resources.getString(R.string.no_internet)
            )
        }
    }

    private fun showLoading() {
        with(binding) {
            pbSearch.visible()
            rvVacancies.invisible()
            ivLookingForPlaceholder.invisible()
            groupPlaceholder.invisible()
            tvCountVacancies.invisible()
        }
    }

    private fun showError() {
        with(binding) {
            rvVacancies.invisible()
            pbSearch.invisible()
            ivLookingForPlaceholder.invisible()
            groupPlaceholder.visible()
            tvCountVacancies.visible()
            tvCountVacancies.text = resources.getText(R.string.not_vacancies)
            imageAndTextHelper.setImageAndText(
                requireContext(),
                ivPlaceholder,
                tvPlaceholder,
                R.drawable.placeholder_no_vacancy_list_or_region_plate_cat,
                resources.getString(R.string.no_vacancy_list)
            )
        }
    }

    private fun showContent(vacancies: List<Vacancy>) {
        with(binding) {
            rvVacancies.visible()
            pbSearch.invisible()
            ivLookingForPlaceholder.invisible()
            vacancyAdapter.updateVacancies(vacancies)
            groupPlaceholder.invisible()
            // Пока что скрою tvCountVacancies, потом нужно будет передать количество найденных вакансий
            tvCountVacancies.invisible()
        }
    }

    private fun configureSearchInput() = binding.etSearch.doOnTextChanged { text, _, _, _ ->
        with(binding.ivEditTextButton) {
            setImageResource(if (text.isNullOrEmpty()) R.drawable.ic_search else R.drawable.ic_close)
            setOnClickListener {
                val inputMethodManager =
                    requireContext().getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                inputMethodManager?.hideSoftInputFromWindow(binding.ivEditTextButton.windowToken, 0)
                binding.etSearch.text.clear()
                viewModel.clearSearch()
                clearFocus()
            }
        }
        text?.let { viewModel.searchDebounce(it.toString()) }
    }

    private fun configureRecycler() {
        binding.rvVacancies.apply {
            adapter = vacancyAdapter
            binding.rvVacancies.addOnScrollListener(object : OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    if (dy > 0) {
                        val pos =
                            (binding.rvVacancies.layoutManager as LinearLayoutManager).findLastVisibleItemPosition()
                        val itemsCount = vacancyAdapter.itemCount
                        if (pos >= itemsCount - 1) {
                            binding.pbSearch.visible()
                            // Добавить элемент лоадера
                            // Callback добавить
                            viewModel.onLastItemReached()
                        }
                    }
                }
            })

        }
    }
}
