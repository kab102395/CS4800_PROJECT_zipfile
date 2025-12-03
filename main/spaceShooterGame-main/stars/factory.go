components {
  id: "script"
  component: "/main/spaceShooterGame-main/stars/factory.script"
}
embedded_components {
  id: "star_factory"
  type: "factory"
  data: "prototype: \"/main/spaceShooterGame-main/stars/star.go\"\n"
  ""
}
embedded_components {
  id: "bonus_factory"
  type: "factory"
  data: "prototype: \"/main/spaceShooterGame-main/stars/bonus_star.go\"\n"
  ""
}
embedded_components {
  id: "bad_factory"
  type: "factory"
  data: "prototype: \"/main/spaceShooterGame-main/stars/bad_star.go\"\n"
  ""
}
