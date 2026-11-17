from typing import Dict, Optional, Union
from pydantic import BaseModel


FeatureValue = Union[float, int, str, bool]


class TabularPredictRequest(BaseModel):
    model_id: Optional[int] = None
    features: Dict[str, FeatureValue]
