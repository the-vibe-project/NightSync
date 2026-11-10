//go:build windows

package main

import (
	"log"

	"golang.org/x/sys/windows/svc"
)

type nightSyncService struct{}

func runAsWindowsService() (bool, error) {
	isService, err := svc.IsWindowsService()
	if err != nil {
		return false, err
	}
	if !isService {
		return false, nil
	}
	return true, svc.Run("NightSync", nightSyncService{})
}

func (nightSyncService) Execute(_ []string, requests <-chan svc.ChangeRequest, status chan<- svc.Status) (bool, uint32) {
	const accepted = svc.AcceptStop | svc.AcceptShutdown
	status <- svc.Status{State: svc.StartPending}

	serverDone := make(chan error, 1)
	go func() {
		serverDone <- runServer()
	}()

	status <- svc.Status{State: svc.Running, Accepts: accepted}
	for {
		select {
		case request := <-requests:
			switch request.Cmd {
			case svc.Interrogate:
				status <- svc.Status{State: svc.Running, Accepts: accepted}
			case svc.Stop, svc.Shutdown:
				status <- svc.Status{State: svc.StopPending}
				select {
				case shutdownRequested <- struct{}{}:
				default:
				}
				if err := <-serverDone; err != nil {
					log.Printf("NightSync service stopped with an error: %v", err)
					return false, 1
				}
				return false, 0
			}
		case err := <-serverDone:
			if err != nil {
				log.Printf("NightSync service stopped with an error: %v", err)
				return false, 1
			}
			return false, 0
		}
	}
}
