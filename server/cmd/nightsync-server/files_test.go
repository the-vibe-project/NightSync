package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func setupFileAPITest(t *testing.T) {
	t.Helper()
	dataDir = t.TempDir()
	auth = &authStore{
		users: map[string]userRecord{
			"alice": {Username: "alice", Role: "member"},
			"bob":   {Username: "bob", Role: "member"},
		},
		sessions: map[string]sessionRecord{
			"alice-token": {Username: "alice", ExpiresAt: time.Now().Add(time.Hour)},
			"bob-token":   {Username: "bob", ExpiresAt: time.Now().Add(time.Hour)},
		},
	}
}

func callFileAPI(handler http.HandlerFunc, method, target, token string, body []byte) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, target, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	if len(body) > 0 {
		req.Header.Set("Content-Type", "application/json")
	}
	res := httptest.NewRecorder()
	handler(res, req)
	return res
}

func TestSafeRelativePathRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	for _, input := range []string{"../outside", "a/../../outside", "/absolute", "C:/outside", "folder\\file", "users.json", "folder/.."} {
		if _, err := safeRelativePath(root, input); err == nil {
			t.Errorf("expected %q to be rejected", input)
		}
	}
	got, err := safeRelativePath(root, "Pictures/2026")
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(got, root) {
		t.Fatalf("resolved path escaped storage root: %s", got)
	}
}

func TestFileManagerFolderUploadDownloadDeleteAndIsolation(t *testing.T) {
	setupFileAPITest(t)

	create := callFileAPI(foldersHandler, http.MethodPost, "/api/v1/folders", "alice-token", []byte(`{"path":"Documents"}`))
	if create.Code != http.StatusCreated {
		t.Fatalf("create folder status=%d body=%s", create.Code, create.Body.String())
	}

	upload := callFileAPI(filesHandler, http.MethodPut, "/api/v1/files?path=Documents%2Fhello.txt", "alice-token", []byte("NightSync file content"))
	if upload.Code != http.StatusCreated {
		t.Fatalf("upload status=%d body=%s", upload.Code, upload.Body.String())
	}

	list := callFileAPI(filesHandler, http.MethodGet, "/api/v1/files?path=Documents", "alice-token", nil)
	if list.Code != http.StatusOK {
		t.Fatalf("list status=%d body=%s", list.Code, list.Body.String())
	}
	var listing struct {
		Items []struct {
			Name      string `json:"name"`
			Path      string `json:"path"`
			IsDir     bool   `json:"is_dir"`
			SizeBytes int64  `json:"size_bytes"`
		} `json:"items"`
	}
	if err := json.Unmarshal(list.Body.Bytes(), &listing); err != nil {
		t.Fatal(err)
	}
	if len(listing.Items) != 1 || listing.Items[0].Name != "hello.txt" || listing.Items[0].IsDir || listing.Items[0].SizeBytes == 0 {
		t.Fatalf("unexpected listing: %+v", listing.Items)
	}

	download := callFileAPI(fileHandler, http.MethodGet, "/api/v1/file?path=Documents%2Fhello.txt", "alice-token", nil)
	if download.Code != http.StatusOK || download.Body.String() != "NightSync file content" {
		t.Fatalf("download status=%d body=%s", download.Code, download.Body.String())
	}

	bobList := callFileAPI(filesHandler, http.MethodGet, "/api/v1/files?path=Documents", "bob-token", nil)
	if bobList.Code != http.StatusNotFound {
		t.Fatalf("another user's folder should not be visible; status=%d", bobList.Code)
	}
	bobRoot := callFileAPI(filesHandler, http.MethodGet, "/api/v1/files", "bob-token", nil)
	if bobRoot.Code != http.StatusOK || strings.Contains(bobRoot.Body.String(), "hello.txt") {
		t.Fatalf("another user can see Alice's file: %s", bobRoot.Body.String())
	}

	deleteFile := callFileAPI(fileHandler, http.MethodDelete, "/api/v1/file?path=Documents%2Fhello.txt", "alice-token", nil)
	if deleteFile.Code != http.StatusOK {
		t.Fatalf("delete status=%d body=%s", deleteFile.Code, deleteFile.Body.String())
	}
	deleteFolder := callFileAPI(foldersHandler, http.MethodDelete, "/api/v1/folders?path=Documents", "alice-token", nil)
	if deleteFolder.Code != http.StatusOK {
		t.Fatalf("delete folder status=%d body=%s", deleteFolder.Code, deleteFolder.Body.String())
	}
}

func TestFileManagerRejectsNonEmptyFolderDeleteAndUploadPathEscape(t *testing.T) {
	setupFileAPITest(t)

	root, err := requestFileRoot(httptest.NewRequest(http.MethodGet, "/", nil))
	if err == nil || root != "" {
		// A request without a token must not obtain a user root.
		t.Fatal("unauthenticated request unexpectedly received a storage root")
	}

	create := callFileAPI(foldersHandler, http.MethodPost, "/api/v1/folders", "alice-token", []byte(`{"path":"Keep"}`))
	if create.Code != http.StatusCreated {
		t.Fatalf("create folder status=%d", create.Code)
	}
	upload := callFileAPI(filesHandler, http.MethodPut, "/api/v1/files?path=Keep%2Finside.txt", "alice-token", []byte("data"))
	if upload.Code != http.StatusCreated {
		t.Fatalf("upload status=%d", upload.Code)
	}
	escape := callFileAPI(filesHandler, http.MethodPut, "/api/v1/files?path=Keep%2F..%2Foutside.txt", "alice-token", []byte("bad"))
	if escape.Code != http.StatusBadRequest {
		t.Fatalf("path escape should be rejected; status=%d", escape.Code)
	}
	nonEmpty := callFileAPI(foldersHandler, http.MethodDelete, "/api/v1/folders?path=Keep", "alice-token", nil)
	if nonEmpty.Code != http.StatusConflict {
		t.Fatalf("non-empty folder should not be deleted; status=%d", nonEmpty.Code)
	}
}

func TestUserStorageDoesNotFollowSymlink(t *testing.T) {
	root := t.TempDir()
	outside := t.TempDir()
	if err := os.WriteFile(filepath.Join(outside, "secret.txt"), []byte("secret"), 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.Symlink(outside, filepath.Join(root, "link")); err != nil {
		t.Skipf("symlink creation unavailable: %v", err)
	}
	if _, err := safeRelativePath(root, "link/secret.txt"); err == nil {
		t.Fatal("expected symlink traversal to be rejected")
	}
}


func TestSafeRelativePathRejectsWindowsReservedAndInvalidNames(t *testing.T) {
	root := t.TempDir()
	invalid := []string{
		"CON", "con.txt", "PRN.log", "AUX", "NUL", "COM1", "COM9.txt",
		"LPT1", "LPT9.csv", "trailing.", "trailing ", "bad?name.txt",
		"bad*name", "bad|name", "bad<name", "bad>name", "bad\"name",
	}
	for _, input := range invalid {
		if _, err := safeRelativePath(root, input); err == nil {
			t.Errorf("expected Windows-invalid name %q to be rejected", input)
		}
	}

	for _, input := range []string{"My Documents", "Résumé.pdf", "2026/Photos/Family"} {
		if _, err := safeRelativePath(root, input); err != nil {
			t.Errorf("expected valid name %q to be accepted: %v", input, err)
		}
	}
}

func TestFileManagerRequiresAuthenticationAndRejectsOverwrite(t *testing.T) {
	setupFileAPITest(t)

	for _, tc := range []struct {
		name string
		handler http.HandlerFunc
		method string
		target string
		body []byte
	}{
		{"list", filesHandler, http.MethodGet, "/api/v1/files", nil},
		{"upload", filesHandler, http.MethodPut, "/api/v1/files?path=x.txt", []byte("x")},
		{"download", fileHandler, http.MethodGet, "/api/v1/file?path=x.txt", nil},
		{"delete", fileHandler, http.MethodDelete, "/api/v1/file?path=x.txt", nil},
		{"create folder", foldersHandler, http.MethodPost, "/api/v1/folders", []byte(`{"path":"x"}`)},
	} {
		t.Run(tc.name, func(t *testing.T) {
			response := callFileAPI(tc.handler, tc.method, tc.target, "", tc.body)
			if response.Code != http.StatusUnauthorized {
				t.Fatalf("expected 401 without authentication, got %d: %s", response.Code, response.Body.String())
			}
		})
	}

	first := callFileAPI(filesHandler, http.MethodPut, "/api/v1/files?path=duplicate.txt", "alice-token", []byte("original"))
	if first.Code != http.StatusCreated {
		t.Fatalf("first upload status=%d body=%s", first.Code, first.Body.String())
	}
	second := callFileAPI(filesHandler, http.MethodPut, "/api/v1/files?path=duplicate.txt", "alice-token", []byte("replacement"))
	if second.Code != http.StatusConflict {
		t.Fatalf("overwrite should be rejected with 409, got %d: %s", second.Code, second.Body.String())
	}
	download := callFileAPI(fileHandler, http.MethodGet, "/api/v1/file?path=duplicate.txt", "alice-token", nil)
	if download.Code != http.StatusOK || download.Body.String() != "original" {
		t.Fatalf("original file was changed after rejected overwrite: status=%d body=%q", download.Code, download.Body.String())
	}
}
