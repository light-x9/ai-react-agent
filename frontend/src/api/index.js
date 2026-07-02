import axios from 'axios'

const API_BASE_URL = process.env.NODE_ENV === 'production' 
 ? '/api'
 : 'http://localhost:8123/api'

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

export const connectSSE = (url, params, onMessage, onError) => {
  const queryString = Object.keys(params)
    .map(key => encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
    .join('&')
  const fullUrl = API_BASE_URL + url + '?' + queryString
  const eventSource = new EventSource(fullUrl)
  eventSource.onmessage = event => {
    let data = event.data
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      if (onMessage) onMessage(data)
    }
  }
  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }
  return eventSource
}


export const chatWithManus = (message) => {
  return connectSSE('/ai/manus/chat', { message })
}

// Upload a file to knowledge base
export const uploadKnowledgeBase = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const response = await request.post('/knowledge-base/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
  return response.data
}

// List uploaded files in knowledge base
export const listKnowledgeFiles = async () => {
  const response = await request.get('/knowledge-base/files')
  return response.data
}

// Delete a file from knowledge base
export const deleteKnowledgeFile = async (sourceName) => {
  const response = await request.delete('/knowledge-base/files/' + encodeURIComponent(sourceName))
  return response.data
}

export default {  chatWithManus,
  uploadKnowledgeBase,
  listKnowledgeFiles,
  deleteKnowledgeFile
}