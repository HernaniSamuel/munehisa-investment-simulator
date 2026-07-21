from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

_ENV_FILE = Path(__file__).resolve().parent.parent / ".env"


class Settings(BaseSettings):
    """Runtime configuration, loaded from environment / .env.

    The .env path is anchored to this module's own directory (src/data-service/.env),
    not the process's working directory, so it doesn't accidentally pick up the repo
    root's .env (Postgres/pgAdmin config for the Java backend) when run from elsewhere.
    """

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE, env_file_encoding="utf-8", env_prefix="DATA_SERVICE_"
    )

    api_key: str
    port: int = 8001


settings = Settings()  # type: ignore[call-arg]  # fields are supplied via env/`.env`
