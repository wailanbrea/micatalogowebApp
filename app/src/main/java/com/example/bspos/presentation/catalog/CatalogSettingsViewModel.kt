package com.example.bspos.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bspos.domain.model.Category
import com.example.bspos.domain.model.UnitOfMeasure
import com.example.bspos.domain.usecase.CategoryUseCases
import com.example.bspos.domain.usecase.UnitOfMeasureUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class CatalogSettingsViewModel @Inject constructor(private val categories:CategoryUseCases,private val units:UnitOfMeasureUseCases):ViewModel(){
 val categoryList=categories.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 val unitList=units.observe().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
 fun addCategory(name:String,description:String){viewModelScope.launch{categories.create(name,description)}}
 fun addUnit(name:String,abbreviation:String){viewModelScope.launch{units.create(name,abbreviation)}}
 fun toggle(category:Category){viewModelScope.launch{categories.update(category,category.name,category.description,category.icon,!category.isActive)}}
 fun toggle(unit:UnitOfMeasure){viewModelScope.launch{units.update(unit,unit.name,unit.abbreviation,!unit.isActive)}}
 fun delete(category:Category){viewModelScope.launch{categories.delete(category.id)}}
 fun delete(unit:UnitOfMeasure){viewModelScope.launch{units.delete(unit.id)}}
 fun update(category:Category,name:String,description:String){viewModelScope.launch{categories.update(category,name,description)}}
 fun update(unit:UnitOfMeasure,name:String,abbreviation:String){viewModelScope.launch{units.update(unit,name,abbreviation)}}
}
