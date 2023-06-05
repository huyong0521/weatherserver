# Weather Service

The Weather Service is a Scala application that provides weather information based on latitude and longitude coordinates using the OpenWeatherMap API.

Note: Only consume the "current" and "alerts" attributes in Open Weather Map response

## Prerequisites
- Java 8 or higher
- Scala 2.13
- sbt (Scala Build Tool) 

## Getting Started
Under project home folder, execute

`sbt run`

The application will start a server on http://localhost:8080 by default

## API Endpoints
### Get Weather

Retrieve weather information for a specific latitude and longitude.

    URL: /weather/{latitude}/{longitude}
    Method: GET
    Parameters:
        latitude (required): Latitude coordinate.
        longitude (required): Longitude coordinate.
        units (optional): Temperature units (default: metric).
    Response:
        Success: Returns weather information in JSON format.
        Failure: Returns an error message.

## Configuration

The application uses the OpenWeatherMap API to retrieve weather data. You need to provide the API configuration by modifying the application.conf file or setting the following environment variables:

- openWeatherMap.apiUrl: The URL of the OpenWeatherMap API.
- openWeatherMap.apiId: Your API key for accessing the OpenWeatherMap API.
- openWeatherMap.apiExclude: Comma-separated values to exclude from the API response (e.g., minutely,hourly,daily).

## Testing

To run the tests, use the following command: 

`sbt test`