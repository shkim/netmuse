package controllers

import (
	"github.com/revel/revel"
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

func (c Admin) Dashboard() revel.Result {
	return c.Render()
}

func (c Admin) MusicList() revel.Result {
	return c.Render()
}

func (c Admin) ArtistList() revel.Result {
	return c.Render()
}

func (c Admin) AlbumList() revel.Result {
	return c.Render()
}

func (c Admin) PlaylistList() revel.Result {
	return c.Render()
}

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


