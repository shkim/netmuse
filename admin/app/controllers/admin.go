package controllers

import (
	"time"
	"strconv"
	"github.com/revel/revel"
	//"github.com/jmoiron/sqlx"
	"gopkg.in/guregu/null.v3"
)

type Admin struct {
	Auth
}

func (c Admin) checkUser() revel.Result {
    user := c.getConnected()
	if user == nil {
		c.Flash.Error("Please sign in.")
		return c.Redirect(Auth.Login)
	}
    
    c.RenderArgs["user"] = user
	return nil
}

const (
	QUICKFIX_SHARE_VIOLATION = 1

	QUICKFIX_ERROR = 99
)
///////////////////////////////////////////////////////////////////////////////

func (c Admin) Dashboard() revel.Result {
	return c.Render()
}

// Update the name of music, if only one file is associated with that music.
func (c Admin) QuickfixMusicTitle() revel.Result {
	title := c.Params.Get("title")
	musicId, _ := strconv.Atoi(c.Params.Get("music_id"))
	//user := c.RenderArgs["user"].(*UserModel)

	var musFiles int
	db.QueryRow(`SELECT COUNT(*) FROM musfile WHERE music_id=?`, musicId).Scan(&musFiles)
	if musFiles > 1 {
		return c.apiErrorMsg(QUICKFIX_SHARE_VIOLATION, "shared")
	}

	_, err := db.Exec("UPDATE music SET name=? WHERE id=?", title, musicId)
	if err != nil {
		return c.apiErrorMsg(QUICKFIX_ERROR, err.Error())
    }
	
	return c.apiOk("updated", nil)
}

// Update the name of artist, if only one file is associated with that artist.
func (c Admin) QuickfixArtistName() revel.Result {
	artist := c.Params.Get("artist")
	artistId, _ := strconv.Atoi(c.Params.Get("artist_id"))
	user := c.RenderArgs["user"].(*UserModel)

	revel.INFO.Printf("name=%s, artist=%d, user=%d\n", artist, artistId, user.Id)

	var numMusics int
	db.QueryRow(`SELECT COUNT(*) FROM music WHERE artist_id=?`, artistId).Scan(&numMusics)
	if numMusics > 1 {
		return c.apiErrorMsg(QUICKFIX_SHARE_VIOLATION, "shared")		
	}

	_, err := db.Exec("UPDATE artist SET name=? WHERE id=?", artist, artistId)
	if err != nil {
		return c.apiErrorMsg(QUICKFIX_ERROR, err.Error())		
    }
	
	return c.apiOk("updated", nil)
}

type photoInfo struct {
    Id int `db:"id" json:"id"`

	Width int `db:"width" json:"width"`
	Height int `db:"height" json:"height"`
	FileSize int `db:"file_size" json:"fileSize"`
	FileType int `db:"file_type" json:"fileType"`
	ObjId string `db:"obj_id" json:"objId"`

	ThumbWidth null.Int `db:"thumb_width" json:"thumbWidth"`
	ThumbHeight null.Int `db:"thumb_height" json:"thumbHeight"`
	ThumbFileSize null.Int `db:"thumb_file_size" json:"thumbFileSize"`
	ThumbFileType null.Int `db:"thumb_file_type" json:"thumbFileType"`
	ThumbObjId null.String `db:"thumb_obj_id" json:"thumbObjId"`
}

func (c Admin) JsonPhotoInfo(photoId int) revel.Result {
	var info photoInfo
	err := db.QueryRowx(`SELECT id, p.width, p.height, p.file_size, p.file_type, p.obj_id,
r.width thumb_width, r.height thumb_height, r.file_size thumb_file_size, 
r.file_type thumb_file_type, r.obj_id thumb_obj_id 
FROM photo p LEFT JOIN resized_photo r ON id WHERE id=?`, photoId).StructScan(&info)
	if err != nil {
		revel.INFO.Printf("PhotoInfo id=%d: %s\n", photoId, err.Error())
		return c.apiErrorMsg(1, err.Error())
	}

	return c.apiOk("ok", info)
}

///////////////////////////////////////////////////////////////////////////////

type musFileListItem struct {
    Id int `db:"id" json:"DT_RowId"`
	CreateTs time.Time `db:"create_ts" json:"creDate"`

	UserId int `db:"user_id" json:"userId"`
	MusicId int `db:"music_id" json:"musicId"`
	ArtistId int `db:"artist_id" json:"artistId"`
	AlbumId int `db:"album_id" json:"albumId"`
	PhotoId null.Int `db:"photo_id" json:"photoId"`

	User string `db:"user_name" json:"user"`	
	Title string `db:"music_name" json:"title"`	
	Artist null.String `db:"artist_name" json:"artist"`	
	Album null.String `db:"album_name" json:"album"`	
	
	Duration int `db:"duration" json:"duration"`
	FileType int `db:"file_type" json:"fileType"`
	FileSize int `db:"file_size" json:"fileSize"`
	ObjId string `db:"obj_id" json:"objId"`
}

func (c Admin) JsonMusicList() revel.Result {
	parm, json := setupJqDt(c)

    db.QueryRow(`SELECT COUNT(*) FROM musfile`).Scan(&json.Total)	
    json.Filtered = json.Total;

	rows, err := db.Queryx(`SELECT musfile.id,user_id,create_ts,duration,file_type,file_size,obj_id,
photo_id, music_id, music.name music_name,
artist.name artist_name, artist_id,
album.name album_name, album_id,
user.display_name user_name
FROM musfile
INNER JOIN user ON user_id=user.id
INNER JOIN music ON music_id=music.id
INNER JOIN artist ON artist_id=artist.id
INNER JOIN album ON album_id=album.id
ORDER BY musfile.id DESC LIMIT ?,?`, parm.SkipRows, parm.FetchRows)        
    
    if err != nil {
        panic(err)
    }

    for rows.Next() {
        var item musFileListItem
        rows.StructScan(&item)		                
        json.append(&item)
    }
           
    return c.RenderJson(json)
}

func (c Admin) MusicList() revel.Result {
	return c.Render()
}

func (c Admin) MusicInfo() revel.Result {
	return c.Render()
}

func (c Admin) MusicFileInfo() revel.Result {
	return c.Render()
}

///////////////////////////////////////////////////////////////////////////////

func (c Admin) ArtistList() revel.Result {
	return c.Render()
}

///////////////////////////////////////////////////////////////////////////////

func (c Admin) AlbumList() revel.Result {
	return c.Render()
}

///////////////////////////////////////////////////////////////////////////////

func (c Admin) PlaylistList() revel.Result {
	return c.Render()
}

///////////////////////////////////////////////////////////////////////////////

func (c Admin) UserList() revel.Result {
	return c.Render()
}

func (c Admin) MyProfile() revel.Result {

	user := c.RenderArgs["user"].(*UserModel)

	var secret string
	err := db.QueryRow(`SELECT secret FROM tool_session WHERE user_id=?`, user.Id).Scan(&secret)
    if err == nil {
    	c.RenderArgs["secret"] = secret
    }

	return c.Render()
}

func (c Admin) UpdateMyProfile() revel.Result {
	user := c.RenderArgs["user"].(*UserModel)
	name := c.Params.Get("dispname")
	photoUrl := c.Params.Get("photourl")

	res, err := db.Exec("UPDATE user SET display_name=?,photo_url=? WHERE id=?", name, photoUrl, user.Id)
    if err != nil {
        c.Flash.Error(err.Error())
    } else {
        nra, err := res.RowsAffected()
        if err != nil {
            c.Flash.Error(err.Error())
        } else if nra == 0 {
            c.Flash.Error("No changes.")
        } else {
            c.Flash.Success("Updated the personal profile.")
        }
    }

	return c.Redirect(Admin.MyProfile)
}

func (c Admin) UpdateToolSession() revel.Result {

	user := c.RenderArgs["user"].(*UserModel)
	secret := c.Params.Get("secret")
    
    _, err := db.Exec("REPLACE INTO tool_session (user_id,secret,create_ts) VALUES(?,?,now())", user.Id, secret)
    if err != nil {
        c.Flash.Error(err.Error())
    } else {
		c.Flash.Success("Updated Tool Session Secret.")
    }

	return c.Redirect(Admin.MyProfile)
}


