export interface ChatRequest {
  message: string;
  sessionId?: string;
}

export interface ChatResponse {
  reponse: string;
}
