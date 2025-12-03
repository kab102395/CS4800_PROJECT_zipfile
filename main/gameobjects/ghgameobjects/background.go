components {
  id: "background1"
  component: "/main/scripts/ghscripts/background.script"
}
embedded_components {
  id: "background"
  type: "sprite"
  data: "default_animation: \"Background\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/atlases/ghatlases/environment.atlas\"\n"
  "}\n"
  ""
}
