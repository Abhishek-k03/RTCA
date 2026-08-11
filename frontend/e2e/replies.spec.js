import { expect, message, openChat, test } from './fixtures'

test('a reply quotes the original and jumps back to it', async ({ createUser, signIn, directChat, send }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'Shall we meet at the harbour on Friday?')
  // enough lines that the original scrolls out of view
  for (let i = 1; i <= 20; i++) await send(i % 2 ? bob : alice, chat, `filler line ${i}`)

  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)
  await openChat(alicePage, chat)
  await openChat(bobPage, chat)

  const original = message(bobPage, 'Shall we meet at the harbour')
  await original.scrollIntoViewIfNeeded()
  await original.hover()
  await original.getByTitle('Reply').click()
  await expect(bobPage.locator('form')).toContainText('Alice Marlowe')
  await bobPage.locator('textarea').fill('Friday works, noon?')
  await bobPage.keyboard.press('Enter')

  const reply = message(alicePage, 'Friday works, noon?')
  await expect(reply).toContainText('Shall we meet at the harbour')
  await reply.getByRole('button', { name: /Shall we meet/ }).click()
  await expect(message(alicePage, 'Shall we meet at the harbour')).toBeInViewport()
})

test('reactions are one per person and sync live', async ({ createUser, signIn, directChat }) => {
  const alice = await createUser('Alice Marlowe')
  const bob = await createUser('Bob Hartley')
  const chat = await directChat(alice, bob, 'we shipped it')

  const alicePage = await signIn(alice)
  const bobPage = await signIn(bob)
  await openChat(alicePage, chat)
  await openChat(bobPage, chat)

  const theirs = message(bobPage, 'we shipped it')
  await theirs.hover()
  await theirs.getByTitle('React').click()
  await bobPage.getByTitle('React ❤️').click()
  await expect(message(alicePage, 'we shipped it').getByTitle('Bob Hartley')).toContainText('1')

  // alice joins in by clicking the chip, then bob takes his back
  await message(alicePage, 'we shipped it').getByRole('button', { name: /❤️/ }).click()
  await expect(message(bobPage, 'we shipped it').getByTitle('You, Alice Marlowe')).toContainText('2')
  await message(bobPage, 'we shipped it').getByTitle('You, Alice Marlowe').click()
  await expect(message(alicePage, 'we shipped it').getByTitle('You')).toContainText('1')
})
