from typing import Optional
from pydantic import BaseModel


class TextPredictRequest(BaseModel):
    text: str
    model_id: Optional[int] = None
