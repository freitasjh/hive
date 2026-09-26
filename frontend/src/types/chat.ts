export interface Message {
  role: 'USER' | 'ASSISTANT'
  message: string
  timestamp: Date
}
