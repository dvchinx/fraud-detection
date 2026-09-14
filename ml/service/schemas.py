from datetime import datetime

from pydantic import BaseModel, Field


class ScoreRequest(BaseModel):
    amount: float = Field(gt=0)
    merchant: str
    country: str
    timestamp: datetime


class FeatureContribution(BaseModel):
    feature: str
    contribution: float


class ScoreResponse(BaseModel):
    riskScore: float
    modelVersion: str
    baseValue: float
    topFactors: list[FeatureContribution]
