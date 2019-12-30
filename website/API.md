# Getting Started with AB2D in Sandbox

#### Getting started

The AB2D API is a RESTful based web service that allows PDP sponsors to retrieve
Part A and Part B claim data. The service provides asynchronous access to the data.
The sequence of steps to request data includes the following steps:

- Request data. A job will be created and a job number returned.
You can request either by contract number:<br>
```GET /api/v1/fhir/Group/{contractNumber}/$export```<br>
or all Part D patients registered with the sponsor:<br>
```GET /api/v1/fhir/Patient/$export```

- Once a job has been created, the user can/should request the status of the submitted job. <br>
```GET /api/v1/fhir/Job/{jobUuid}/$status```<br>
The job will either be in progress or completed. The application will limit the frequency in which
a job status may be queried. The value of "retry-after" passed back in the response header should
indicate a minimum amount of time between status checks. Once the job is complete, this request will 
respond with the list of files containing the bulk data or any error messages.

- Once the search job has been completed, the contents of the of the created file(s) can be 
downloaded by using:<br>
```GET /api/v1/fhir/Job/{jobUuid}/file/{filename}```<br>
The file(s) are specified as the output of the status request. The files will only be available
for 24 hours after the job completes. Files are also unavailable after they have been successfully
downloaded. The contents of the file will be in ndjson 
(<a href="http://ndjson.org/">New Line Delimited JSON</a>) of FHIR explanation of benefit objects 
(Based on the <a href="https://www.hl7.org/fhir/overview.html">HL7 FHIR</a> standard)
- A job may be cancelled at any point during its processing:<br>
```DELETE /api/v1/fhir/Job/{jobUuid}/$status```

### Authentication and Authorization
The API uses the JSON Web Tokens (JWT) to authorize use of the endpoints. The
token should be sent using the "Authorization" header field with the value specified
as "Bearer xxxxx" where xxxxx is the value of the JWT. The <a href="swagger-ui.html">Swagger page</a>
allows the requester to test the api by by clicking on the "Authorize" button and then specifying 
a "Bearer xxxxx" value. Once you authorize the swagger page, all endpoints will add that 
Authorization header to their request and the lock next to the end point will go from 
unlocked to locked. To verify that this is working, inspection of the generated curl statement
should include the token in the header.

