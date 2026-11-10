//go:build !windows

package main

import (
	"syscall"
	"unsafe"
)

func diskSpace(path string) (total uint64, free uint64, err error) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(path, &st); err != nil {
		return 0, 0, err
	}
	total = st.Blocks * uint64(st.Bsize)
	free = st.Bavail * uint64(st.Bsize)
	_ = unsafe.Sizeof(st)
	return total, free, nil
}
