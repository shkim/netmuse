package controllers

import (
    "os"
	"fmt"
	"time"
	"path"
    "strings"
	"io/ioutil"
	"encoding/json"
    //_ "github.com/go-sql-driver/mysql"
    "github.com/jmoiron/sqlx"
	"gopkg.in/mgo.v2"
    "github.com/revel/revel"
	firebase "github.com/wuman/firebase-server-sdk-go"
)

type FbAppConfig struct {
    ApiKey string `json:"apiKey"`
    AuthDomain string `json:"authDomain"`
    DatabaseURL string `json:"databaseURL"`
	StorageBucket string `json:"storageBucket"`
	MessagingSenderId string `json:"messagingSenderId"`
}

var (
    db *sqlx.DB

	mgoSession *mgo.Session
    mgoDB *mgo.Database
	mgoFS *mgo.GridFS

	fbApp *firebase.App
	fbConfig FbAppConfig
)

func initDB() {
    dbDriver, _ := revel.Config.String("db.driver")
    dbDsn, _ := revel.Config.String("db.dsn")
    
    db = sqlx.MustConnect(dbDriver, dbDsn)
    revel.INFO.Printf("MySQL DSN=%s\n", dbDsn)

    db.MustExec("SET time_zone = '+9:00'")
    time.Local = time.FixedZone("Asia/Seoul", 9*60*60)

	mgoDsn, _ := revel.Config.String("mgo.dsn")
    revel.INFO.Printf("MongoDB DSN=%s\n", mgoDsn)
    var err error
    mgoSession, err = mgo.Dial(mgoDsn)
    if err != nil {
        panic(err)
    }

    mgoSession.SetMode(mgo.Monotonic, true)
    mgoDB = mgoSession.DB("")
	mgoFS = mgoDB.GridFS("fs")
}

// TODO
func initMongoSchema() {
    c := mgoDB.C("coll")

    index := mgo.Index{
        Key:        []string{"name", "title"},
        Unique:     true,
        DropDups:   true,
        Background: true,
        Sparse:     true,
    }

    err := c.EnsureIndex(index)
    if err != nil {
        panic(err)
    }
}

func initFirebase() {
	appConfFile, _ := revel.Config.String("firebase.appConfig")
	appConfPath := path.Join(revel.BasePath, appConfFile)

	svcAcntFile, _ := revel.Config.String("firebase.serviceAccount")
	svcAcntPath := path.Join(revel.BasePath, svcAcntFile)

	revel.INFO.Printf("Firebase appConfig: %s", appConfPath)
	revel.INFO.Printf("Firebase serviceAccount: %s", svcAcntPath)

	file, err := ioutil.ReadFile(appConfPath)
    if err != nil {
		panic(fmt.Sprintf("Firebase appConfig file read error: %v", err))
    }

	err = json.Unmarshal(file, &fbConfig)
	if err != nil {
		panic(fmt.Sprintf("Firebase appConfig file parsing error: %v", err))
	}

	if _, err := os.Stat(svcAcntPath); err != nil {
		panic(fmt.Sprintf("Firebase service account file not found: %s", svcAcntPath))
	}

	fbApp, err = firebase.InitializeApp(&firebase.Options{
		ServiceAccountPath: svcAcntPath,
	})

	if err != nil {
		panic(fmt.Sprintf("Firebase.InitializeApp failed: %s", err))
	}

}

func init() {
	revel.OnAppStart(initFirebase)
	revel.OnAppStart(initDB)

    revel.TemplateFuncs["hasPrefix"] = func(a, b string) bool { 
        return strings.HasPrefix(a,b)
    }
    
    revel.InterceptMethod(Auth.setUser, revel.BEFORE)
    revel.InterceptMethod(Admin.checkUser, revel.BEFORE)    
}
