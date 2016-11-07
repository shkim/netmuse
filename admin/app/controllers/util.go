package controllers

import (
	"github.com/revel/revel"
	"strings"   
    "crypto/rand"
    "encoding/base64"
)

func getIpAddr(c *revel.Controller) string {
	ipaddr := c.Request.Header.Get("X-Forwarded-For")
    if ipaddr == "" {
        ipaddr = strings.Split(c.Request.RemoteAddr, ":")[0]
    }

	return ipaddr
}

// GenerateRandomBytes returns securely generated random bytes. 
// It will return an error if the system's secure random
// number generator fails to function correctly, in which
// case the caller should not continue.
func generateRandomBytes(n int) ([]byte, error) {
    b := make([]byte, n)
    _, err := rand.Read(b)
    // Note that err == nil only if we read len(b) bytes.
    if err != nil {
        return nil, err
    }

    return b, nil
}

// GenerateRandomString returns a URL-safe, base64 encoded
// securely generated random string.
// It will return an error if the system's secure random
// number generator fails to function correctly, in which
// case the caller should not continue.
func generateRandomString(s int) (string, error) {
    b, err := generateRandomBytes(s)
    return base64.URLEncoding.EncodeToString(b), err
}
