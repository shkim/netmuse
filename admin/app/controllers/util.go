package controllers

import (
	"strings"   
    "strconv"
    "crypto/rand"
    "encoding/base64"
	"github.com/revel/revel"
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

///////////////////////////////////////////////////////////////////////////////

type jqDataTable struct {
    Draw int `json:"draw"`
    Total int `json:"recordsTotal"`
    Filtered int `json:"recordsFiltered"`
    Rows []interface{} `json:"data"`
}

type jqDtParam struct {
    SkipRows int
    FetchRows int
    SearchKeyword string
    OrderDir string
    OrderColumn int
}

func setupJqDt(c Admin) (prm jqDtParam, json jqDataTable) {
    prm.SkipRows, _ = strconv.Atoi(c.Params.Get("start"))
    prm.FetchRows, _ = strconv.Atoi(c.Params.Get("length"))
    if prm.FetchRows <= 0 {
        prm.FetchRows = 15
    }
    
    prm.SearchKeyword = c.Params.Get("search[value]")
    if prm.SearchKeyword != "" {
        prm.SearchKeyword = "%"+prm.SearchKeyword+"%"
    }
    
    prm.OrderDir = c.Params.Get("order[0][dir]")
    prm.OrderColumn, _ = strconv.Atoi(c.Params.Get("order[0][column]"))
    
    json.Draw, _ = strconv.Atoi(c.Params.Get("draw"))
    json.Rows = make([]interface{}, 0)
    
    return
}

func (ret *jqDataTable) append(ptr interface{}) {
    ret.Rows = append(ret.Rows, ptr)
}
