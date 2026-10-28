CREATE TABLE weather_reading (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    city TEXT NOT NULL CHECK (length(trim(city)) > 0),
    temperature_celsius NUMERIC NOT NULL
);
CREATE INDEX weather_reading_city_requested_at_idx ON weather_reading (city, requested_at DESC);
