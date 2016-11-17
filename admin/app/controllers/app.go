package controllers

import (
	"github.com/revel/revel"
)

type App struct {
	*revel.Controller
}

func (c App) Index() revel.Result {
	return c.Redirect(App.Player)
}

func (c App) PlayerLogin() revel.Result {
	c.RenderArgs["fbApiKey"] = fbConfig.ApiKey
	c.RenderArgs["fbAuthDomain"] = fbConfig.AuthDomain	
	return c.Render()
}

func (c App) Player() revel.Result {
	c.RenderArgs["fbApiKey"] = fbConfig.ApiKey
	c.RenderArgs["fbAuthDomain"] = fbConfig.AuthDomain	
	return c.Render()
}
