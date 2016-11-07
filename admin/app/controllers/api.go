package controllers

import (
	"strconv"
	"errors"
	"bytes"
	"io"
	"io/ioutil"
	"fmt"
	"image"
	_ "image/jpeg"
	_ "image/png"
	"crypto/md5"
	"encoding/json"
	"encoding/hex"
	"mime/multipart"
	"database/sql"
	"gopkg.in/mgo.v2/bson"
	"github.com/revel/revel"
)

type MuseApi struct {
	*revel.Controller
}

const (
	API_INVALID_PARAM = 1
	API_AUTH_FAILED = 10
	API_SESSION_INITFAIL = 20
	API_INTERNAL_SERVER_ERROR = 99


	IMGFMT_JPEG = 1
	IMGFMT_PNG = 2
	IMGFMT_WEBP = 3

	MUSFMT_MP3 = 1
	MUSFMT_OGG = 2
	MUSFMT_FLAC = 3
)

type apiResult struct {
	Code int         `json:"code"`
	Msg  string      `json:"msg"`
	Data interface{} `json:"data,omitempty"`
}

func (c MuseApi) apiOk(msg string, data interface{}) revel.Result {
	return c.RenderJson(apiResult{Code: 0, Msg: msg, Data: data})
}

func (c MuseApi) apiError0(errCode int) revel.Result {
	var msg string
	switch(errCode) {
	case API_INVALID_PARAM:
		msg = "Invalid parameters"
	case API_AUTH_FAILED:
		msg = "AuthFailed"
	case API_SESSION_INITFAIL:
		msg = "Session init failed"
	case API_INTERNAL_SERVER_ERROR:
		msg = "Internal Server Error"
	default:
		msg = "ERROR"
	}
	
	return c.RenderJson(apiResult{Code:errCode, Msg:msg, Data:nil})
}

func (c MuseApi) apiErrorMsg(errCode int, msg string) revel.Result {
	return c.RenderJson(apiResult{Code:errCode, Msg:msg, Data:nil})
}

type retUrl struct {
	Url string `json:"url"`
}

func (c MuseApi) GetUrl(target string) revel.Result {
	var ret retUrl
	if target == "pclogin" {
		ret.Url = "/api/_page/login"
	} else {
		return c.apiErrorMsg(API_INVALID_PARAM, "Unknown target " + target)
	}

	return c.apiOk("OK", &ret)
}

func (c MuseApi) PcLogin() revel.Result {
	return c.Render()
}


func (c MuseApi) LoginTool() revel.Result {
	email := c.Params.Get("email")
	secret := c.Params.Get("secret")

	var validSecret string
	var userId int
	err := db.QueryRow(`SELECT secret, user_id FROM user INNER JOIN tool_session ON id=user_id WHERE email=?`, email).Scan(&validSecret, &userId)
	if err != nil {
		return c.apiErrorMsg(API_AUTH_FAILED, "Unknown user or tool secret not initialized")
	}

	if secret != validSecret {
		return c.apiErrorMsg(API_AUTH_FAILED, "Secret not matched")
	}

	// generate session key
	sessKey, err := generateRandomString(48)
	if err != nil {
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, "Session key generation failed")
	}

	_, err = db.Exec("UPDATE tool_session SET sesskey=?, access_ts=now() WHERE user_id=?", sessKey, userId)
	if err != nil {
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
    }

	var data = map[string]string {"sesskey":sessKey}	

	return c.apiOk("OK", &data)
}

func getFileBytesAndMD5(fh *multipart.FileHeader) ([]byte, string, error) {
	file, err := fh.Open()
	if err != nil {
		return nil, "", err
	}

	defer file.Close()
	bits, err := ioutil.ReadAll(file)
	if err != nil {
		return nil, "", err
	}

	md5sum := md5.Sum(bits)
	md5hex := hex.EncodeToString(md5sum[:])
	return bits, md5hex, nil
}

func saveAndGetPhotoId(photoData []byte, photoMD5 string) (int64, error) {
	fileSize := len(photoData)
	var photoId int64
	err := db.QueryRow(`SELECT id FROM photo WHERE file_size=? AND file_md5=?`, fileSize, photoMD5).Scan(&photoId)
	if err == nil {
		revel.INFO.Printf("Use cached image id=%d\n", photoId)
		return photoId, nil	
	}

	var fileType int
	conf, format, err := image.DecodeConfig(bytes.NewReader(photoData))
	if format == "jpeg" {
		fileType = IMGFMT_JPEG
		format = "jpg"
	} else if format == "png" {
		fileType = IMGFMT_PNG
	} else if format == "webp" {
		fileType = IMGFMT_WEBP
	} else {
		return 0, errors.New("Unsupported image format: " + format)
	}

	res, err := db.Exec("INSERT INTO photo (width,height,file_type,file_size,file_md5) VALUES(?,?,?,?,?)",
		conf.Width, conf.Height, fileType, fileSize, photoMD5)
    if err != nil {
		return 0, err
    }
	
	photoId, err = res.LastInsertId()
	if err != nil {
		return 0, err
	}

	gfile, err := mgoFS.Create(fmt.Sprintf("i%d.%s", photoId, format))
	if err != nil {
		return 0, err
	}

	io.Copy(gfile, bytes.NewReader(photoData))
	err = gfile.Close()
	if err != nil {
		panic("GFile close failed " + err.Error())
		return 0, err
	}

	objId := gfile.Id().(bson.ObjectId)
	db.MustExec("UPDATE photo SET obj_id=? WHERE id=?", objId.Hex(), photoId)

	return photoId, nil
}

func saveAndGetAlbumId(name string) int64 {
	if name == "" {
		return 0
	}

	var albumId int64
	err := db.QueryRow(`SELECT id FROM album WHERE name=?`, name).Scan(&albumId)
	if err == nil {
		revel.INFO.Printf("Use cached album id=%d\n", albumId)
		return albumId	
	}

	res, err := db.Exec("INSERT INTO album (name) VALUES(?)", name)
    if err != nil {
		revel.ERROR.Printf("Insert Album (%s) failed: %s\n", name, err.Error())
		return 0
    }
	
	albumId, err = res.LastInsertId()
	if err != nil {
		revel.ERROR.Printf("Album LastInsertId (%s) failed: %s\n", name, err.Error())
		return 0
	}

	return albumId
}

func saveAndGetArtistId(name string) int64 {
	if name == "" {
		return 0
	}

	var artistId int64
	err := db.QueryRow(`SELECT id FROM artist WHERE name=?`, name).Scan(&artistId)
	if err == nil {
		revel.INFO.Printf("Use cached artist id=%d\n", artistId)
		return artistId	
	}

	res, err := db.Exec("INSERT INTO artist (name) VALUES(?)", name)
    if err != nil {
		revel.ERROR.Printf("Insert Artist (%s) failed: %s\n", name, err.Error())
		return 0
    }
	
	artistId, err = res.LastInsertId()
	if err != nil {
		revel.ERROR.Printf("Artist LastInsertId (%s) failed: %s\n", name, err.Error())
		return 0
	}

	return artistId
}

func (c MuseApi) getUserIdFromSesskey() int {
	sesskey := c.Params.Get("sesskey")

	var userId int
	err := db.QueryRow(`SELECT user_id FROM tool_session WHERE sesskey=?`, sesskey).Scan(&userId)
	if err == nil {
		return userId
	}

	revel.ERROR.Printf("ToolSession not found: %s\n", sesskey)
	return 0
}

type someMetaInfo struct {
	Seconds int `json:"seconds"`
	Genre string `json:"genre"`
}

func (c MuseApi) UploadMusic() revel.Result {

	userId := c.getUserIdFromSesskey()
	if userId == 0 {
		return c.apiErrorMsg(API_AUTH_FAILED, "Invalid session key")
	}

	// check music file format (mp3,ogg,flac)
	var fileType int
	fileExt := c.Params.Get("filetype")
	if fileExt == "mp3" {
		fileType = MUSFMT_MP3
	} else if fileExt == "ogg" {
		fileType = MUSFMT_OGG
	} else {
		return c.apiErrorMsg(API_INVALID_PARAM, "Unsupported music file format: " + fileExt)
	}

	// check meta tags json
	var durationSeconds int
	var genre sql.NullString
	var metaTags sql.NullString
	metaTags.String = c.Params.Get("meta")
	metaTags.Valid = (metaTags.String != "")
	if metaTags.Valid {
		var metaInfo someMetaInfo
		err := json.Unmarshal([]byte(metaTags.String), &metaInfo)
		if err != nil {
			return c.apiErrorMsg(API_INVALID_PARAM, "Invalid Meta info: " + err.Error())
		}
    
		durationSeconds = metaInfo.Seconds
		genre.String = metaInfo.Genre
		genre.Valid = genre.String != "" 
	}

	// handle music file
	fhMusic := c.Params.Files["music"]
	if len(fhMusic) != 1 {
		return c.apiErrorMsg(API_INVALID_PARAM, "Music data empty")
	}

	musicData, musicMD5, err := getFileBytesAndMD5(fhMusic[0])
	if err != nil {
		revel.ERROR.Printf("get fhMusic failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
	}

	//fileName := c.Params.Get("filename")
	fileSize, _ := strconv.Atoi(c.Params.Get("filesize"))
	fileMD5 := c.Params.Get("filemd5")

	if fileMD5 != musicMD5 || fileSize != len(musicData) {
		return c.apiErrorMsg(API_INVALID_PARAM, "Music data inconsistency error")
	}


	var photoId sql.NullInt64
	fhImage := c.Params.Files["image"]
	if len(fhImage) > 0 {
		imgSize, _ := strconv.Atoi(c.Params.Get("imgsize"))
		imgMD5 := c.Params.Get("imgmd5")
		photoData, photoMD5, err := getFileBytesAndMD5(fhImage[0])
		if err != nil {
			revel.ERROR.Printf("get fhImage failed: %s\n", err.Error())
			return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
		}
		
		if imgMD5 != photoMD5 || imgSize != len(photoData) {
			return c.apiErrorMsg(API_INVALID_PARAM, "Image data inconsistency error")
		}

		photoId.Int64, err = saveAndGetPhotoId(photoData, photoMD5)
		if err != nil {
			revel.ERROR.Printf("saveAndGetPhotoId failed: %s\n", err.Error())
			return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
		}

		photoId.Valid = true
	}


	res, err := db.Exec("INSERT INTO musfile (user_id,photo_id,file_type,file_size,file_md5,duration,meta_tags) VALUES(?,?,?,?,?,?,?)",
		userId, photoId, fileType, fileSize, fileMD5, durationSeconds, metaTags)
    if err != nil {
		revel.ERROR.Printf("Insert musfile failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
    }

	musFileId, err := res.LastInsertId()
	if err != nil {
		revel.ERROR.Printf("musfile LastInsertId failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
	}

	// save music data to GridFS
	gfile, err := mgoFS.Create(fmt.Sprintf("m%d.%s", musFileId, fileExt))
	if err != nil {
		revel.ERROR.Printf("musfile GridFS.Create failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
	}

	io.Copy(gfile, bytes.NewReader(musicData))
	err = gfile.Close()
	if err != nil {
		revel.ERROR.Printf("musfile GFile.Close failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
	}

	objId := gfile.Id().(bson.ObjectId)

	var artistId, albumId sql.NullInt64
	artistId.Int64 = saveAndGetArtistId(c.Params.Get("artist"))
	artistId.Valid = artistId.Int64 != 0 
	albumId.Int64 = saveAndGetAlbumId(c.Params.Get("album"))
	albumId.Valid = albumId.Int64 != 0
	musTitle := c.Params.Get("title")

	res, err = db.Exec("INSERT INTO music (name,file_id,artist_id,album_id,genre) VALUES(?,?,?,?,?)",
		musTitle, musFileId, artistId, albumId, genre)
    if err != nil {
		revel.ERROR.Printf("Insert music failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
    }

	musicId, err := res.LastInsertId()
	if err != nil {
		revel.ERROR.Printf("music LastInsertId failed: %s\n", err.Error())
		return c.apiErrorMsg(API_INTERNAL_SERVER_ERROR, err.Error())
	}

	db.MustExec("UPDATE musfile SET music_id=?, obj_id=? WHERE id=?", musicId, objId.Hex(), musFileId)

	var data = map[string]interface{} { "music_id":musicId, "file_id":musFileId }	

	return c.apiOk("OK", &data)
}