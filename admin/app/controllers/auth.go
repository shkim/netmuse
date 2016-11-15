package controllers

import (
	"github.com/revel/revel"
	firebase "github.com/wuman/firebase-server-sdk-go"
)

type Auth struct {
    App
}

type UserModel struct {
    Id          int     `db:"id"`
	Uid			string	`db:"uid"`	
    Email		string  `db:"email"`
    Name        string  `db:"display_name"`
    PhotoUrl	string  `db:"photo_url"`

	CanLogin	bool	`db:"can_login"`
	CanUpload	bool	`db:"can_upload"`
	IsAdmin		bool	`db:"is_admin"`
}

func (c Auth) getConnected() *UserModel {
	if c.RenderArgs["user"] != nil {
		return c.RenderArgs["user"].(*UserModel)
	}
	if uid, ok := c.Session["sessUid"]; ok {
		return getUserByUid(uid)
	}
	return nil
}

func (c Auth) setUser() revel.Result {
	if user := c.getConnected(); user != nil {
		c.RenderArgs["user"] = user
	}
	return nil
}

func getUserByUid(uid string) *UserModel {    
    var user UserModel
    err := db.QueryRowx("SELECT id,uid,email,display_name,photo_url,can_login,can_upload,is_admin FROM user WHERE uid=?", uid).StructScan(&user)
    if err != nil {
        return nil
    }
    
	return &user;
}

func (c Auth) Index() revel.Result {
    if c.getConnected() != nil {
        return c.Redirect(Admin.Dashboard)
    }
    
    return c.Redirect(Auth.Login)
}

func (c Auth) Login() revel.Result {
	c.RenderArgs["fbApiKey"] = fbConfig.ApiKey
	c.RenderArgs["fbAuthDomain"] = fbConfig.AuthDomain	
	return c.Render()
}

func (c Auth) Logout() revel.Result {
	for k := range c.Session {
		delete(c.Session, k)
	}
	return c.Redirect(Auth.Login)
}

func (c Auth) LoginWithToken(token string) revel.Result {
    //revel.INFO.Printf("token=%s", token)
	fbAuth, err := firebase.GetAuth()
	decodedToken, err := fbAuth.VerifyIDToken(token)

	ret := make(map[string]interface{})
	ret["result"] = "error"

	if err != nil {		
		ret["error"] = err
		return c.RenderJson(ret)
	}

	uid, found := decodedToken.UID()
	if !found {
		ret["error"] = "Invalid token"
		return c.RenderJson(ret)
	}

	var user UserModel
	rowx := db.QueryRowx("SELECT can_login,can_upload,is_admin FROM user WHERE uid=?", uid)
	if rowx.Err() != nil {
		ret["error"] = rowx.Err()
		return c.RenderJson(ret)
	}
	
	err = rowx.StructScan(&user)
	if err == nil {
		if !user.IsAdmin {
			ret["error"] = "관리자만 접근 가능합니다."
			return c.RenderJson(ret)			
		}
		
		// Admin. Ready to proceed
		//db.MustExec("UPDATE web_user SET last_login=now() WHERE _id=?", user.Id)
		c.Session["sessUid"] = uid;	
		c.Session.SetNoExpiration()
		//c.Session.SetDefaultExpiration()
		ret["result"] = "ok"
		ret["url"] = "/admin/dashboard"
		return c.RenderJson(ret)
	}
	
	// no info in local db: insert new
	dispName, _ := decodedToken.Name()
	email, _ := decodedToken.Email()
	photo, _ := decodedToken.Picture()

	res, err := db.Exec("INSERT INTO user (uid,email,display_name,photo_url) VALUES(?,?,?,?)", uid, email, dispName, photo)

	if err != nil {
		ret["error"] = err.Error()
	} else {
		_, err := res.RowsAffected()
		if err != nil {
			ret["error"] = err.Error()
		} else {
			ret["error"] = "관리자의 확인을 기다리는 중입니다."  
		}
	}	
	
	return c.RenderJson(ret)
}
