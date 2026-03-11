import axios from 'axios'

const http = axios.create({ baseURL: '/' })

export const createTask = (formData) => http.post('/api/tasks/create', formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
})

export const refreshTask = (id) => http.post(`/api/tasks/${id}/refresh`)

export const getTaskDetail = (id) => http.get(`/api/tasks/${id}`)

export const getTaskList = (page = 1, size = 20) => http.get('/api/tasks/list', { params: { page, size } })

export const downloadTask = (id) => http.get(`/api/tasks/${id}/download`, { responseType: 'blob' })
