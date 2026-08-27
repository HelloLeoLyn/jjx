import _Toolbar from './Toolbar.vue'
import _DataTable from './DataTable.vue'
import _SearchForm from './SearchForm.vue'
import _DialogForm from './DialogForm.vue'
import type { Component } from 'vue'

export const DataTable: Component = _DataTable
export const SearchForm: Component = _SearchForm
export const DialogForm: Component = _DialogForm
export const Toolbar: Component = _Toolbar
export default {
  DataTable,
  SearchForm,
  Toolbar,
}
