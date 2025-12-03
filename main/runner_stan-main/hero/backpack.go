components {
  id: "backpack"
  component: "/main/runner_stan-main/hero/backpack.script"
}
embedded_components {
  id: "sprite"
  type: "sprite"
  data: "default_animation: \"Goose1\"\n"
  "material: \"/builtins/materials/sprite.material\"\n"
  "textures {\n"
    "  sampler: \"texture_sampler\"\n"
  "  texture: \"/main/runner_stan-main/hero/hero.atlas\"\n"
  "}\n"
  ""
}
