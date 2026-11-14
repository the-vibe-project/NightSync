# NightSync 0.9 — Local API smoke test

This guide describes the current authenticated local-development API. It replaces the old v0.4 instructions, which used obsolete file routes and did not require authentication.

## Safety and prerequisites
- Run the current NightSync-Server Windows application and complete its first-run wizard.
- The bundled server listens only on 127.0.0.1:8787.
- Run these commands on the host PC. Do not expose this HTTP development server to a LAN or the internet.
- File operations require a bearer token and each account has a private user directory.
- Uploads are limited to 100 MB. Existing files are not overwritten.

## Check server health
~~~powershell
Invoke-RestMethod 'http://127.0.0.1:8787/health'
~~~

The health endpoint is public. GET /api/v1/status requires an authenticated bearer token.

## Sign in and obtain a token
Create the first owner account through the Server UI wizard, then sign in:

~~~powershell
$body = @{ username = 'YOUR_USERNAME'; password = 'YOUR_PASSWORD' } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/api/v1/login' -ContentType 'application/json' -Body $body
$token = $login.token
$headers = @{ Authorization = "Bearer $token" }
~~~

Keep the token private. Sessions expire after 12 hours or when the server restarts.

## Check authenticated status
~~~powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8787/api/v1/status' -Headers $headers
~~~

## Create a folder and upload a file
~~~powershell
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:8787/api/v1/folders' -Headers $headers -ContentType 'application/json' -Body (@{ path = 'QA-Smoke-Test' } | ConvertTo-Json)
Invoke-WebRequest -Method Put -Uri 'http://127.0.0.1:8787/api/v1/files?path=QA-Smoke-Test%2Fhello.txt' -Headers $headers -InFile '.\hello.txt'
~~~

## List and download
~~~powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8787/api/v1/files?path=QA-Smoke-Test' -Headers $headers
Invoke-WebRequest -Uri 'http://127.0.0.1:8787/api/v1/file?path=QA-Smoke-Test%2Fhello.txt' -Headers $headers -OutFile '.\hello-downloaded.txt'
~~~

## Delete test data
File deletion is permanent. Only delete the test file created above:

~~~powershell
Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8787/api/v1/file?path=QA-Smoke-Test%2Fhello.txt' -Headers $headers
Invoke-RestMethod -Method Delete -Uri 'http://127.0.0.1:8787/api/v1/folders?path=QA-Smoke-Test' -Headers $headers
~~~

The server rejects path traversal, symbolic links, invalid Windows filenames, unauthenticated file access, duplicate uploads, and deletion of non-empty folders.

## Known limitations
This smoke test validates the local API only. It does not demonstrate phone-to-PC connectivity, TLS, real scheduled synchronization, offline retry, conflict resolution, backup/restore, or production security. Do not store important data in this prototype.
