from fastapi import FastAPI

from service import model
from service.schemas import ScoreRequest, ScoreResponse

app = FastAPI(title="Fraud Detection - ML Service")


@app.get("/health")
def health():
    return {"status": "ok", "modelVersion": model.MODEL_VERSION}


@app.post("/score", response_model=ScoreResponse)
def score_transaction(request: ScoreRequest) -> ScoreResponse:
    risk_score, base_value, top_factors = model.score(request.amount, request.timestamp)
    return ScoreResponse(
        riskScore=risk_score,
        modelVersion=model.MODEL_VERSION,
        baseValue=base_value,
        topFactors=top_factors,
    )
