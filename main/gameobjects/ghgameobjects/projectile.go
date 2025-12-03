components {
  id: "projectile"
  component: "/main/scripts/ghscripts/projectile.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"rock\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
  "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/atlases/ghatlases/sprites.atlas\"\n"
  "}\n"
  ""
  position {
    x: 2.0
    y: -27.0
  }
}
