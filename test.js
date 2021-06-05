async function main() {
  await sleep()
  console.log('Hello')
}

async function sleep() {
  return new Promise(resolve => {
    const a = 1 + 2
  })
}

sleep().then(() => console.log('Done'))
